const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');
const cors = require('cors')({ origin: true });
const crypto = require('crypto');
const calendarService = require('./googleCalendarService');
require('dotenv').config();

admin.initializeApp();

// ── Tier Configuration (Single Source of Truth) ──────────────────────────────

const TIER_CONFIG = {
  ASTEROID: { level: 0, price: 0,      label: 'Asteroid' },
  MOON:     { level: 1, price: 49999,   label: 'Moon' },
  EARTH:    { level: 2, price: 99999,   label: 'Earth' },
  SUN:      { level: 3, price: 199999,  label: 'Sun' },
};

// Legacy tier name mapping
const LEGACY_TIER_MAP = {
  'EXPLORER': 'ASTEROID',
  'MEMBER': 'MOON',
  'INNER_CIRCLE': 'EARTH',
  'FOUNDER': 'SUN',
};

function normalizeTierName(tierName) {
  const upper = (tierName || '').toUpperCase().replace(/ /g, '_');
  return LEGACY_TIER_MAP[upper] || upper;
}

// ── LinkedIn OAuth (Google Cloud Secret Manager) ────────────────────────────

exports.exchangeLinkedInCode = functions
  .runWith({ secrets: ['LINKEDIN_CLIENT_ID', 'LINKEDIN_CLIENT_SECRET'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    // Enable CORS preflight
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { code, uid, redirectUri } = req.body;

      if (!code) {
        res.status(400).json({ error: 'Missing authorization code.' });
        return;
      }
      if (!uid) {
        res.status(400).json({ error: 'Missing user ID.' });
        return;
      }
      if (!redirectUri) {
        res.status(400).json({ error: 'Missing redirect URI.' });
        return;
      }

      const clientId = process.env.LINKEDIN_CLIENT_ID;
      const clientSecret = process.env.LINKEDIN_CLIENT_SECRET;

      if (!clientId || !clientSecret) {
        res.status(500).json({ error: 'LinkedIn API credentials are not configured on the server.' });
        return;
      }

      console.log(`Exchanging LinkedIn code for uid: ${uid}`);

      // 1. Exchange authorization code for access token
      const tokenUrl = 'https://www.linkedin.com/oauth/v2/accessToken';
      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: redirectUri,
        client_id: clientId,
        client_secret: clientSecret
      });

      let tokenResponse;
      try {
        tokenResponse = await axios.post(tokenUrl, tokenParams.toString(), {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        });
      } catch (err) {
        console.error('LinkedIn Access Token Request failed:', err.response ? err.response.data : err.message);
        res.status(400).json({
          error: 'Failed to exchange authorization code for token.',
          details: err.response ? err.response.data : err.message
        });
        return;
      }

      const accessToken = tokenResponse.data.access_token;
      if (!accessToken) {
        res.status(400).json({ error: 'LinkedIn did not return an access token.' });
        return;
      }

      // 2. Fetch user profile from LinkedIn OpenID Connect UserInfo endpoint
      const userInfoUrl = 'https://api.linkedin.com/v2/userinfo';
      let profileResponse;
      try {
        profileResponse = await axios.get(userInfoUrl, {
          headers: {
            'Authorization': `Bearer ${accessToken}`
          }
        });
      } catch (err) {
        console.error('LinkedIn UserInfo Request failed:', err.response ? err.response.data : err.message);
        res.status(400).json({
          error: 'Failed to fetch user profile from LinkedIn.',
          details: err.response ? err.response.data : err.message
        });
        return;
      }

      const profileData = profileResponse.data;
      // profileData structure includes: sub, name, given_name, family_name, picture, email, email_verified
      const linkedinName = profileData.name || `${profileData.given_name || ''} ${profileData.family_name || ''}`.trim() || 'LinkedIn User';
      const linkedinEmail = profileData.email || '';
      const linkedinPicture = profileData.picture || '';
      const linkedinId = profileData.sub || '';

      console.log(`Successfully fetched LinkedIn profile for: ${linkedinName}`);

      // 3. Update the user document in Firestore
      const userRef = admin.firestore().collection('users').doc(uid);
      const userSnap = await userRef.get();

      const updateData = {
        isLinkedInConnected: true,
        linkedInProfile: {
          name: linkedinName,
          email: linkedinEmail,
          avatarUrl: linkedinPicture,
          linkedinId: linkedinId,
          connectedAt: admin.firestore.FieldValue.serverTimestamp()
        },
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      };

      // Proactively merge name and avatar if the user doesn't have one set
      if (userSnap.exists) {
        const currentData = userSnap.data();
        if (!currentData.name || currentData.name === 'Builder') {
          updateData.name = linkedinName;
        }
        if (!currentData.avatarUrl && linkedinPicture) {
          updateData.avatarUrl = linkedinPicture;
        }
      } else {
        // If profile doesn't exist, create it with LinkedIn details
        updateData.name = linkedinName;
        updateData.avatarUrl = linkedinPicture;
        updateData.email = linkedinEmail;
        updateData.createdAt = admin.firestore.FieldValue.serverTimestamp();
      }

      await userRef.set(updateData, { merge: true });

      res.status(200).json({
        success: true,
        message: 'LinkedIn account connected successfully.',
        profile: {
          name: linkedinName,
          avatarUrl: linkedinPicture,
          email: linkedinEmail
        }
      });

    } catch (error) {
      console.error('LinkedIn auth function error:', error);
      res.status(500).json({ error: 'Internal server error occurred.', details: error.message });
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════════
// ══════════════════════════════════════════════════════════════════════════════
// ── COSMOS Lifetime Membership & Gift Card — Cloud Functions ──────────────────
// ══════════════════════════════════════════════════════════════════════════════

/**
 * validateGiftCard
 *
 * Validates a gift card code and returns its current stored balance and status.
 * Input: { code }
 * Output: { success, valid, code, initialValue, currentBalance, status }
 */
exports.validateGiftCard = functions.https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { code: rawCode } = req.body;
      if (!rawCode) {
        res.status(400).json({ error: 'Gift card code is required.' });
        return;
      }

      const code = rawCode.trim().toUpperCase();
      const cardRef = admin.firestore().collection('gift_cards').doc(code);
      const cardDoc = await cardRef.get();

      if (!cardDoc.exists) {
        res.status(404).json({ error: `Gift card '${code}' was not found.`, valid: false });
        return;
      }

      const cardData = cardDoc.data();
      const status = cardData.status || 'ACTIVE';
      const currentBalance = Number(cardData.currentBalance) || 0;
      const initialValue = Number(cardData.initialValue) || currentBalance;
      const expiresAt = cardData.expiresAt || null;

      const isExpired = expiresAt && (typeof expiresAt === 'number' ? expiresAt : expiresAt.toMillis()) < Date.now();
      const isValid = status === 'ACTIVE' && currentBalance > 0 && !isExpired;

      res.status(200).json({
        success: true,
        valid: isValid,
        code: code,
        initialValue: initialValue,
        currentBalance: currentBalance,
        currency: cardData.currency || 'INR',
        status: isExpired ? 'EXPIRED' : (currentBalance <= 0 ? 'EXHAUSTED' : status),
        title: cardData.title || 'Cosmic Gift Voucher',
        description: cardData.description || 'Redeemable towards COSMOS membership'
      });
    } catch (error) {
      console.error('validateGiftCard error:', error);
      res.status(500).json({ error: 'Failed to validate gift card.', details: error.message });
    }
  });
});

/**
 * createMembershipOrder
 *
 * Creates an order for a lifetime membership upgrade, applying any gift card discount.
 * Differential amount is calculated; if differential is 0, a free order is returned.
 *
 * Input: { targetTier: "MOON" | "EARTH" | "SUN", uid, giftCardCode? }
 * Output: { orderId, amount, differentialAmount, giftCardDiscount, preservedBalance, isFreeOrder, keyId, currentTier, targetTier }
 */
exports.createMembershipOrder = functions
  .runWith({ secrets: ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { targetTier: rawTargetTier, uid, giftCardCode: rawGiftCardCode } = req.body;

      if (!uid) {
        res.status(401).json({ error: 'Authentication required.' });
        return;
      }
      if (!rawTargetTier) {
        res.status(400).json({ error: 'Missing targetTier.' });
        return;
      }

      const targetTier = normalizeTierName(rawTargetTier);
      if (!TIER_CONFIG[targetTier]) {
        res.status(400).json({ error: `Invalid target tier: ${rawTargetTier}` });
        return;
      }

      // 1. Read user's current membership tier from Firestore
      const userRef = admin.firestore().collection('users').doc(uid);
      const userDoc = await userRef.get();

      if (!userDoc.exists) {
        res.status(404).json({ error: 'User not found.' });
        return;
      }

      const userData = userDoc.data();
      const rawCurrentTier = normalizeTierName(userData.membershipTier || 'ASTEROID');
      const currentTier = TIER_CONFIG[rawCurrentTier] ? rawCurrentTier : 'ASTEROID';

      // 2. Validate upgrade path
      const currentLevel = TIER_CONFIG[currentTier].level;
      const targetLevel = TIER_CONFIG[targetTier].level;

      if (targetLevel <= currentLevel) {
        res.status(400).json({
          error: 'Cannot downgrade or select current tier.',
          currentTier: currentTier,
          targetTier: targetTier
        });
        return;
      }

      // 3. Calculate full upgrade amount
      const currentPrice = TIER_CONFIG[currentTier].price;
      const targetPrice = TIER_CONFIG[targetTier].price;
      const upgradeAmount = targetPrice - currentPrice;

      if (upgradeAmount <= 0) {
        res.status(400).json({ error: 'Invalid upgrade amount.' });
        return;
      }

      // 4. Validate and apply Gift Card if provided
      let giftCardDiscount = 0;
      let preservedBalance = 0;
      let appliedGiftCardCode = null;

      if (rawGiftCardCode && typeof rawGiftCardCode === 'string' && rawGiftCardCode.trim().length > 0) {
        const giftCardCode = rawGiftCardCode.trim().toUpperCase();
        const cardRef = admin.firestore().collection('gift_cards').doc(giftCardCode);
        const cardDoc = await cardRef.get();

        if (cardDoc.exists) {
          const cardData = cardDoc.data();
          const balance = Number(cardData.currentBalance) || 0;
          const status = cardData.status || 'ACTIVE';

          if (status === 'ACTIVE' && balance > 0) {
            appliedGiftCardCode = giftCardCode;
            giftCardDiscount = Math.min(balance, upgradeAmount);
            preservedBalance = balance - giftCardDiscount;
          }
        }
      }

      const differentialAmount = Math.max(0, upgradeAmount - giftCardDiscount);
      const isFreeOrder = differentialAmount === 0;

      // 5. Generate or Create Razorpay Order
      let orderId = `order_cosmos_${crypto.randomBytes(8).toString('hex')}`;
      let razorpayKeyId = process.env.RAZORPAY_KEY_ID || '';
      const amountInPaise = differentialAmount * 100;

      if (!isFreeOrder) {
        const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;
        if (razorpayKeyId && razorpayKeySecret) {
          const orderPayload = {
            amount: amountInPaise,
            currency: 'INR',
            receipt: `cosmos_${uid.substring(0, 8)}_${Date.now()}`,
            notes: {
              user_id: uid,
              current_tier: currentTier,
              target_tier: targetTier,
              upgrade_amount: upgradeAmount,
              gift_card_code: appliedGiftCardCode || 'NONE',
              gift_card_discount: giftCardDiscount,
              differential_amount: differentialAmount,
              plan_type: 'lifetime',
              source: 'cosmos_android_app'
            }
          };

          const authHeader = Buffer.from(`${razorpayKeyId}:${razorpayKeySecret}`).toString('base64');
          try {
            const orderResponse = await axios.post('https://api.razorpay.com/v1/orders', orderPayload, {
              headers: {
                'Content-Type': 'application/json',
                'Authorization': `Basic ${authHeader}`
              }
            });
            orderId = orderResponse.data.id;
          } catch (err) {
            console.error('Razorpay order creation warning:', err.response ? err.response.data : err.message);
          }
        }
      }

      // 6. Store pending order in Firestore
      await admin.firestore().collection('membership_orders').doc(orderId).set({
        userId: uid,
        currentTier: currentTier,
        targetTier: targetTier,
        amount: upgradeAmount,
        differentialAmount: differentialAmount,
        giftCardCode: appliedGiftCardCode,
        giftCardDiscount: giftCardDiscount,
        preservedBalance: preservedBalance,
        amountInPaise: amountInPaise,
        currency: 'INR',
        razorpayOrderId: orderId,
        isFreeOrder: isFreeOrder,
        status: 'PENDING',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      console.log(`Created membership order ${orderId} for ${uid}: ${currentTier} → ${targetTier}, Total ₹${upgradeAmount}, Card: ${appliedGiftCardCode || 'None'} (−₹${giftCardDiscount}), To Pay: ₹${differentialAmount}`);

      res.status(200).json({
        success: true,
        orderId: orderId,
        amount: upgradeAmount,
        differentialAmount: differentialAmount,
        giftCardDiscount: giftCardDiscount,
        preservedBalance: preservedBalance,
        isFreeOrder: isFreeOrder,
        amountInPaise: amountInPaise,
        currency: 'INR',
        keyId: razorpayKeyId,
        currentTier: currentTier,
        targetTier: targetTier,
        tierLabel: TIER_CONFIG[targetTier].label
      });

    } catch (error) {
      console.error('createMembershipOrder error:', error);
      res.status(500).json({ error: 'Internal server error.', details: error.message });
    }
  });
});

/**
 * verifyMembershipPayment
 *
 * Verifies a Razorpay payment signature, updates user membership,
 * and atomically deducts any applied gift card balance.
 */
exports.verifyMembershipPayment = functions
  .runWith({ secrets: ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { uid, orderId, paymentId, signature } = req.body;

      if (!uid || !orderId || !paymentId || !signature) {
        res.status(400).json({ error: 'Missing required fields: uid, orderId, paymentId, signature.' });
        return;
      }

      const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;
      if (razorpayKeySecret) {
        const expectedSignature = crypto
          .createHmac('sha256', razorpayKeySecret)
          .update(`${orderId}|${paymentId}`)
          .digest('hex');

        if (expectedSignature !== signature) {
          console.error(`Signature mismatch for order ${orderId}. Expected: ${expectedSignature}, Got: ${signature}`);
          res.status(400).json({ error: 'Payment signature verification failed.' });
          return;
        }
      }

      const orderRef = admin.firestore().collection('membership_orders').doc(orderId);
      const orderDoc = await orderRef.get();

      if (!orderDoc.exists) {
        res.status(404).json({ error: 'Order not found.' });
        return;
      }

      const orderData = orderDoc.data();

      if (orderData.status === 'COMPLETED' && orderData.razorpayPaymentId === paymentId) {
        res.status(200).json({
          success: true,
          newTier: orderData.targetTier,
          badge: TIER_CONFIG[orderData.targetTier]?.label || orderData.targetTier,
          alreadyProcessed: true
        });
        return;
      }

      if (orderData.status !== 'PENDING') {
        res.status(400).json({ error: `Order is in unexpected state: ${orderData.status}` });
        return;
      }

      if (orderData.userId !== uid) {
        res.status(403).json({ error: 'Order does not belong to this user.' });
        return;
      }

      const { targetTier, currentTier, amount, differentialAmount, giftCardCode, giftCardDiscount } = orderData;
      const now = admin.firestore.FieldValue.serverTimestamp();

      const batch = admin.firestore().batch();

      // 1. If gift card was used, deduct from gift card balance
      let preservedBalance = 0;
      if (giftCardCode && giftCardDiscount > 0) {
        const cardRef = admin.firestore().collection('gift_cards').doc(giftCardCode);
        const cardDoc = await cardRef.get();
        if (cardDoc.exists) {
          const cardData = cardDoc.data();
          const currBal = Number(cardData.currentBalance) || 0;
          const newBal = Math.max(0, currBal - giftCardDiscount);
          preservedBalance = newBal;
          const newStatus = newBal <= 0 ? 'EXHAUSTED' : 'ACTIVE';

          batch.update(cardRef, {
            currentBalance: newBal,
            status: newStatus,
            lastRedeemedAt: Date.now(),
            redemptions: admin.firestore.FieldValue.arrayUnion({
              userId: uid,
              orderId: orderId,
              amountDeducted: giftCardDiscount,
              previousBalance: currBal,
              newBalance: newBal,
              targetTier: targetTier,
              timestamp: Date.now()
            })
          });
        }
      }

      // 2. Update user's membership tier
      const userRef = admin.firestore().collection('users').doc(uid);
      batch.update(userRef, {
        membershipTier: targetTier,
        updatedAt: now
      });

      // 3. Mark order as completed
      batch.update(orderRef, {
        status: 'COMPLETED',
        razorpayPaymentId: paymentId,
        razorpaySignature: signature,
        completedAt: now
      });

      // 4. Create subscription record
      const subRef = admin.firestore().collection('users').doc(uid).collection('subscriptions').doc();
      batch.set(subRef, {
        tier: targetTier,
        status: 'ACTIVE',
        isLifetime: true,
        startDate: Date.now(),
        razorpayPaymentId: paymentId,
        razorpayOrderId: orderId,
        amountPaid: differentialAmount || 0,
        giftCardDiscount: giftCardDiscount || 0,
        giftCardCode: giftCardCode || null,
        upgradedFrom: currentTier,
        createdAt: now
      });

      // 5. Create payment record
      const paymentRef = admin.firestore().collection('payments').doc();
      batch.set(paymentRef, {
        userId: uid,
        paymentId: paymentId,
        orderId: orderId,
        amount: differentialAmount || 0,
        giftCardDiscount: giftCardDiscount || 0,
        giftCardCode: giftCardCode || null,
        tier: targetTier,
        upgradedFrom: currentTier,
        status: 'SUCCESS',
        isLifetime: true,
        planType: 'lifetime',
        timestamp: now
      });

      // 6. Create notification
      const notifRef = admin.firestore().collection('notifications').doc();
      batch.set(notifRef, {
        userId: uid,
        type: 'COMMUNITY_ANNOUNCEMENT',
        title: 'Membership Upgraded! 🚀',
        body: `Welcome to the ${TIER_CONFIG[targetTier]?.label || targetTier} tier. Your COSMOS universe has expanded. Lifetime access unlocked.`,
        actionId: paymentId,
        isRead: false,
        timestamp: now
      });

      await batch.commit();

      console.log(`✅ Membership upgraded for user ${uid}: ${currentTier} → ${targetTier}, Paid: ₹${differentialAmount}, Card Discount: ₹${giftCardDiscount}`);

      res.status(200).json({
        success: true,
        newTier: targetTier,
        tierLabel: TIER_CONFIG[targetTier]?.label || targetTier,
        badge: getBadgeName(targetTier),
        amount: differentialAmount,
        giftCardDiscount: giftCardDiscount,
        preservedBalance: preservedBalance,
        paymentId: paymentId
      });

    } catch (error) {
      console.error('verifyMembershipPayment error:', error);
      res.status(500).json({ error: 'Internal server error.', details: error.message });
    }
  });
});

/**
 * createEventTicketOrder
 *
 * Creates a standard Razorpay order for purchasing a paid event ticket.
 * All ticket payments are collected centrally into the platform's Razorpay account.
 *
 * Input: { eventId, uid, userName, userEmail, userContact }
 * Output: { success, orderId, amount, amountInPaise, currency, keyId, eventTitle, eventId }
 */
exports.createEventTicketOrder = functions
  .runWith({ secrets: ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { eventId, uid, userName, userEmail, userContact } = req.body;

      if (!uid) {
        res.status(401).json({ error: 'Authentication required.' });
        return;
      }
      if (!eventId) {
        res.status(400).json({ error: 'Missing eventId.' });
        return;
      }

      // 1. Fetch Event Document
      const eventRef = admin.firestore().collection('events').doc(eventId);
      const eventDoc = await eventRef.get();

      if (!eventDoc.exists) {
        res.status(404).json({ error: 'Event not found.' });
        return;
      }

      const eventData = eventDoc.data();

      // Check if event is paid
      if (!eventData.isPaid) {
        res.status(400).json({ error: 'This is a free event. Registration does not require payment.' });
        return;
      }

      // Check capacity
      const currentParticipants = eventData.participantCount || 0;
      const maxParticipants = eventData.maxParticipants || 100;
      if (currentParticipants >= maxParticipants) {
        res.status(400).json({ error: 'Event is already sold out.' });
        return;
      }

      // Check if user is already registered
      const regRef = eventRef.collection('registrants').doc(uid);
      const regDoc = await regRef.get();
      if (regDoc.exists) {
        res.status(400).json({ error: 'You are already registered for this event.' });
        return;
      }

      // Determine ticket price
      let ticketAmount = 0;
      if (typeof eventData.priceAmount === 'number' && eventData.priceAmount > 0) {
        ticketAmount = Math.round(eventData.priceAmount);
      } else if (eventData.price) {
        const cleaned = String(eventData.price).replace(/[^0-9.]/g, '');
        ticketAmount = Math.round(parseFloat(cleaned) || 0);
      }

      if (ticketAmount <= 0) {
        res.status(400).json({ error: 'Invalid event ticket price.' });
        return;
      }

      const amountInPaise = ticketAmount * 100;
      const razorpayKeyId = process.env.RAZORPAY_KEY_ID || '';
      const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;

      let orderId = `order_evt_${crypto.randomBytes(8).toString('hex')}`;

      if (razorpayKeyId && razorpayKeySecret) {
        const orderPayload = {
          amount: amountInPaise,
          currency: 'INR',
          receipt: `evt_${eventId.substring(0, 6)}_${uid.substring(0, 6)}_${Date.now()}`,
          notes: {
            event_id: eventId,
            event_title: eventData.title || 'Cosmos Event',
            user_id: uid,
            user_name: userName || 'Cosmos Member',
            user_email: userEmail || '',
            type: 'event_ticket',
            source: 'cosmos_platform'
          }
        };

        const authHeader = Buffer.from(`${razorpayKeyId}:${razorpayKeySecret}`).toString('base64');
        try {
          const orderResponse = await axios.post('https://api.razorpay.com/v1/orders', orderPayload, {
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Basic ${authHeader}`
            }
          });
          orderId = orderResponse.data.id;
        } catch (err) {
          console.error('Razorpay event order creation warning:', err.response ? err.response.data : err.message);
        }
      }

      // Store pending order in Firestore
      await admin.firestore().collection('event_orders').doc(orderId).set({
        orderId: orderId,
        eventId: eventId,
        eventTitle: eventData.title || 'Cosmos Event',
        userId: uid,
        userName: userName || 'Cosmos Member',
        userEmail: userEmail || '',
        userContact: userContact || '',
        amount: ticketAmount,
        amountInPaise: amountInPaise,
        currency: 'INR',
        status: 'PENDING',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      console.log(`Created event ticket order ${orderId} for event ${eventId} (${eventData.title}), user: ${uid}, amount: ₹${ticketAmount}`);

      res.status(200).json({
        success: true,
        orderId: orderId,
        eventId: eventId,
        eventTitle: eventData.title || 'Cosmos Event',
        amount: ticketAmount,
        amountInPaise: amountInPaise,
        currency: 'INR',
        keyId: razorpayKeyId
      });

    } catch (error) {
      console.error('createEventTicketOrder error:', error);
      res.status(500).json({ error: 'Internal server error.', details: error.message });
    }
  });
});

/**
 * verifyEventTicketPayment
 *
 * Verifies Razorpay HMAC signature for a ticket purchase, atomically records registrant,
 * logs event payment, updates participant count, and creates notifications.
 *
 * Input: { uid, eventId, orderId, paymentId, signature, userName, userEmail }
 * Output: { success, eventId, receiptId, amount, paymentId, orderId, paidAt }
 */
exports.verifyEventTicketPayment = functions
  .runWith({ secrets: ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { uid, eventId, orderId, paymentId, signature, userName, userEmail } = req.body;

      if (!uid || !eventId || !orderId || !paymentId || !signature) {
        res.status(400).json({ error: 'Missing required fields: uid, eventId, orderId, paymentId, signature.' });
        return;
      }

      // Verify HMAC signature
      const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;
      if (razorpayKeySecret) {
        const expectedSignature = crypto
          .createHmac('sha256', razorpayKeySecret)
          .update(`${orderId}|${paymentId}`)
          .digest('hex');

        if (expectedSignature !== signature) {
          console.error(`Signature mismatch for event order ${orderId}. Expected: ${expectedSignature}, Got: ${signature}`);
          res.status(400).json({ error: 'Payment signature verification failed.' });
          return;
        }
      }

      const orderRef = admin.firestore().collection('event_orders').doc(orderId);
      const orderDoc = await orderRef.get();

      if (!orderDoc.exists) {
        res.status(404).json({ error: 'Event order not found.' });
        return;
      }

      const orderData = orderDoc.data();

      // Check if order was already completed
      if (orderData.status === 'COMPLETED' && orderData.razorpayPaymentId === paymentId) {
        res.status(200).json({
          success: true,
          alreadyProcessed: true,
          eventId: eventId,
          receiptId: orderData.receiptId || `COSMOS-TKT-${orderId.slice(-6).toUpperCase()}`
        });
        return;
      }

      if (orderData.userId !== uid) {
        res.status(403).json({ error: 'Order does not belong to this user.' });
        return;
      }

      const eventRef = admin.firestore().collection('events').doc(eventId);
      const eventDoc = await eventRef.get();

      if (!eventDoc.exists) {
        res.status(404).json({ error: 'Event not found.' });
        return;
      }

      const eventData = eventDoc.data();
      const now = admin.firestore.FieldValue.serverTimestamp();
      const receiptId = `COSMOS-TKT-${Date.now().toString(36).toUpperCase()}-${crypto.randomBytes(2).toString('hex').toUpperCase()}`;
      const amountPaid = orderData.amount || (eventData.priceAmount || 0);
      const participantName = userName || orderData.userName || 'Cosmos Member';
      const participantEmail = userEmail || orderData.userEmail || '';

      const batch = admin.firestore().batch();

      // 1. Update event orders record
      batch.update(orderRef, {
        status: 'COMPLETED',
        razorpayPaymentId: paymentId,
        razorpaySignature: signature,
        receiptId: receiptId,
        completedAt: now
      });

      // 2. Register user under event registrants
      const registrantRef = eventRef.collection('registrants').doc(uid);
      batch.set(registrantRef, {
        userId: uid,
        name: participantName,
        email: participantEmail,
        registeredAt: now,
        paymentStatus: 'CONFIRMED',
        transactionId: paymentId,
        orderId: orderId,
        receiptId: receiptId,
        amountPaid: amountPaid,
        paymentMethod: 'RAZORPAY'
      });

      // 3. Record event payment details
      const paymentRef = eventRef.collection('payments').doc(uid);
      batch.set(paymentRef, {
        participantId: uid,
        participantName: participantName,
        participantEmail: participantEmail,
        eventId: eventId,
        eventTitle: eventData.title || orderData.eventTitle,
        amount: amountPaid,
        currency: 'INR',
        paymentMethod: 'RAZORPAY',
        transactionId: paymentId,
        orderId: orderId,
        paymentStatus: 'CONFIRMED',
        paidAt: now,
        receiptId: receiptId,
        collectedCentrally: true
      });

      // 4. Increment participant count on event
      batch.update(eventRef, {
        participantCount: admin.firestore.FieldValue.increment(1)
      });

      // 5. Record platform global payment
      const globalPaymentRef = admin.firestore().collection('payments').doc();
      batch.set(globalPaymentRef, {
        userId: uid,
        eventId: eventId,
        eventTitle: eventData.title || orderData.eventTitle,
        paymentId: paymentId,
        orderId: orderId,
        receiptId: receiptId,
        amount: amountPaid,
        currency: 'INR',
        type: 'EVENT_TICKET',
        status: 'SUCCESS',
        paymentMethod: 'RAZORPAY',
        timestamp: now
      });

      // 6. Send notification to participant
      const notifRef = admin.firestore().collection('notifications').doc();
      batch.set(notifRef, {
        userId: uid,
        type: 'EVENT_REMINDER',
        title: `Ticket Confirmed: ${eventData.title || 'Event'} 🎟️`,
        body: `Your ticket payment of ₹${amountPaid} was successful. Pass Receipt: ${receiptId}. See you there!`,
        actionId: eventId,
        isRead: false,
        timestamp: now
      });

      // 7. Send notification to host
      const hostId = eventData.createdBy;
      if (hostId && hostId !== uid) {
        const hostNotifRef = admin.firestore().collection('notifications').doc();
        batch.set(hostNotifRef, {
          userId: hostId,
          type: 'EVENT_REGISTRATION',
          title: `🎟️ New Ticket Sold: ${participantName}`,
          body: `${participantName} purchased a ticket for ₹${amountPaid} for "${eventData.title}".`,
          actionId: eventId,
          isRead: false,
          timestamp: now
        });
      }

      await batch.commit();

      console.log(`✅ Ticket payment verified for user ${uid} on event ${eventId} (${eventData.title}). Payment ID: ${paymentId}, Receipt: ${receiptId}`);

      res.status(200).json({
        success: true,
        eventId: eventId,
        eventTitle: eventData.title || orderData.eventTitle,
        receiptId: receiptId,
        amount: amountPaid,
        paymentId: paymentId,
        orderId: orderId,
        paidAt: Date.now()
      });

    } catch (error) {
      console.error('verifyEventTicketPayment error:', error);
      res.status(500).json({ error: 'Internal server error.', details: error.message });
    }
  });
});

function getBadgeName(tier) {
  const badges = {
    'ASTEROID': 'Explorer',
    'MOON': 'Lunar Member',
    'EARTH': 'Earth Member',
    'SUN': 'Solar Elite'
  };
  return badges[tier] || tier;
}

/**
 * generateAiContent
 *
 * Securely calls the Gemini API on the backend using the GEMINI_API_KEY from Google Cloud Secret Manager.
 *
 * Input: { action, prompt }
 * Output: { success, text }
 */
exports.generateAiContent = functions
  .runWith({ secrets: ['GEMINI_API_KEY'] })
  .https.onRequest((req, res) => {
  return cors(req, res, async () => {
    if (req.method === 'OPTIONS') {
      res.status(204).send('');
      return;
    }

    try {
      const { action, prompt } = req.body;

      if (!action) {
        res.status(400).json({ error: 'Missing action parameter.' });
        return;
      }
      if (!prompt) {
        res.status(400).json({ error: 'Missing prompt parameter.' });
        return;
      }

      const apiKey = process.env.GEMINI_API_KEY;

      if (!apiKey || apiKey.trim() === '') {
        console.warn('GEMINI_API_KEY is not configured in Cloud Secrets. Using simulation fallback.');
        const simulatedText = simulateAiContent(action, prompt);
        res.status(200).json({ success: true, text: simulatedText, simulated: true });
        return;
      }

      const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=${apiKey}`;
      const payload = {
        contents: [
          {
            parts: [
              {
                text: prompt
              }
            ]
          }
        ]
      };

      const apiResponse = await axios.post(url, payload, {
        headers: {
          'Content-Type': 'application/json'
        }
      });

      const text = apiResponse.data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (!text) {
        throw new Error('Invalid response structure from Gemini API');
      }

      res.status(200).json({ success: true, text: text });

    } catch (error) {
      console.error('generateAiContent error:', error.response ? error.response.data : error.message);
      res.status(500).json({ 
        error: 'Failed to generate AI content.', 
        details: error.response ? JSON.stringify(error.response.data) : error.message 
      });
    }
  });
});

function simulateAiContent(action, prompt) {
  if (action === 'meetingSummary') {
    return `✦ AI Meeting Summary (Simulated) ✦
• Discussed: Enterprise scaling strategy, NexusAI seed round closing, and target customer profiles.
• Decisions Made: To run a pilot validation test on Sequoia's portfolio network.
• Next Steps: Schedule a 30-minute intro call this week. Follow up with pitch deck details.
• Open Questions: Target MRR benchmarks, fundraising timelines, and valuation caps.`;
  } else if (action === 'chatCrmSummary') {
    return `✦ AI Relationship Summary (Simulated) ✦
• Private Goal: Explore professional collaboration
• Next Step: Schedule follow-up call
• Recommended Follow-up: "Hi, let's connect for 15 minutes to align on the project goals we discussed."`;
  } else {
    // Event description fallback: try to pull event info from prompt if possible
    let detailMsg = "Join us for our upcoming Cosmos event. Connect with top members of the community for an evening of high-value networking, knowledge sharing, and collaborative opportunities. We look forward to seeing you there!";
    if (prompt.includes("Title:")) {
      try {
        const titleMatch = prompt.match(/Title:\s*(.+)/);
        const locMatch = prompt.match(/Location:\s*(.+)/);
        if (titleMatch) {
          const title = titleMatch[1].trim();
          const loc = locMatch ? locMatch[1].trim() : "Virtual";
          detailMsg = `Join us for our upcoming '${title}' in ${loc}. Connect with top members of the Cosmos community for an evening of high-value networking, knowledge sharing, and collaborative opportunities. We look forward to seeing you there!`;
        }
      } catch (e) {
        // use default
      }
    }
    return detailMsg;
  }
}

// ── Google Calendar + Meet: Virtual Event Management ──────────────────────────

/**
 * createVirtualEvent
 * 
 * Creates a Firestore event AND a Google Calendar event with auto-generated
 * Google Meet link. Participants are automatically added as Calendar attendees
 * when they register.
 * 
 * Body: { title, description, date, time, maxParticipants, isPaid, price,
 *         currency, priceAmount, coverUrl, tags, creatorId, creatorEmail,
 *         paymentUpiId, paymentAccountName, paymentInstructions }
 * 
 * Returns: { success, eventId, meetLink, calendarEventId }
 */
exports.createVirtualEvent = functions
  .runWith({
    secrets: ['GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY'],
    timeoutSeconds: 30,
    memory: '256MB',
  })
  .https.onRequest((req, res) => {
    return cors(req, res, async () => {
      if (req.method === 'OPTIONS') {
        res.status(204).send('');
        return;
      }

      try {
        const {
          title,
          description = '',
          date,
          time,
          maxParticipants = 50,
          isPaid = false,
          price = '',
          currency = 'INR',
          priceAmount = 0,
          coverUrl = '',
          tags = [],
          creatorId,
          creatorEmail = '',
          paymentUpiId = '',
          paymentAccountName = '',
          paymentInstructions = '',
        } = req.body;

        if (!title || !date || !time || !creatorId) {
          res.status(400).json({ error: 'Missing required fields: title, date, time, creatorId' });
          return;
        }

        // 1. Create Google Calendar event with Meet link
        let calendarEventId = '';
        let meetLink = '';

        try {
          const calResult = await calendarService.createCalendarEvent({
            title: `[Cosmos] ${title}`,
            description: description || `Cosmos virtual event: ${title}`,
            dateStr: date,
            timeStr: time,
            durationMinutes: 60,
            attendeeEmails: creatorEmail ? [creatorEmail] : [],
          });
          calendarEventId = calResult.calendarEventId;
          meetLink = calResult.meetLink;
          console.log(`Calendar event created: ${calendarEventId}, Meet: ${meetLink}`);
        } catch (calError) {
          // Calendar creation failed — proceed without Meet link
          // This allows graceful degradation if service account isn't configured
          console.warn('Google Calendar event creation failed (proceeding without Meet link):', calError.message);
          meetLink = 'https://meet.google.com/new'; // Fallback: user creates their own
        }

        // 2. Create Firestore event document
        const eventData = {
          title,
          description,
          date,
          time,
          location: meetLink || 'Virtual',
          type: 'OPEN_NETWORKING',
          participantCount: 0,
          maxParticipants,
          isPaid,
          price,
          currency,
          priceAmount,
          coverUrl,
          tags,
          createdBy: creatorId,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          // Virtual event specific fields
          isVirtual: true,
          meetLink,
          calendarEventId,
        };

        if (isPaid) {
          eventData.paymentUpiId = paymentUpiId;
          eventData.paymentAccountName = paymentAccountName;
          eventData.paymentInstructions = paymentInstructions;
        }

        const docRef = await admin.firestore().collection('events').add(eventData);
        console.log(`Firestore event created: ${docRef.id}`);

        res.status(200).json({
          success: true,
          eventId: docRef.id,
          meetLink,
          calendarEventId,
        });

      } catch (error) {
        console.error('createVirtualEvent error:', error);
        res.status(500).json({
          error: 'Failed to create virtual event',
          details: error.message,
        });
      }
    });
  });

/**
 * addEventParticipant
 * 
 * Registers a participant for an event AND adds them as a Google Calendar
 * attendee so they receive the Meet link and calendar invite automatically.
 * 
 * Body: { eventId, userId, name, email }
 * Returns: { success }
 */
exports.addEventParticipant = functions
  .runWith({
    secrets: ['GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY'],
    timeoutSeconds: 15,
    memory: '256MB',
  })
  .https.onRequest((req, res) => {
    return cors(req, res, async () => {
      if (req.method === 'OPTIONS') {
        res.status(204).send('');
        return;
      }

      try {
        const { eventId, userId, name, email } = req.body;

        if (!eventId || !userId || !email) {
          res.status(400).json({ error: 'Missing required fields: eventId, userId, email' });
          return;
        }

        const db = admin.firestore();
        const eventRef = db.collection('events').doc(eventId);
        const eventDoc = await eventRef.get();

        if (!eventDoc.exists) {
          res.status(404).json({ error: 'Event not found' });
          return;
        }

        const eventData = eventDoc.data();
        const calendarEventId = eventData.calendarEventId || '';

        // Add to Google Calendar if it's a virtual event with a Calendar event
        if (calendarEventId && email) {
          try {
            await calendarService.addAttendeeToEvent(calendarEventId, email);
            console.log(`Added ${email} to Calendar event ${calendarEventId}`);
          } catch (calError) {
            console.warn(`Failed to add ${email} to Calendar event:`, calError.message);
            // Don't fail the registration if Calendar sync fails
          }
        }

        res.status(200).json({ success: true });

      } catch (error) {
        console.error('addEventParticipant error:', error);
        res.status(500).json({
          error: 'Failed to add participant to calendar',
          details: error.message,
        });
      }
    });
  });

/**
 * removeEventParticipant
 * 
 * Removes a participant from the Google Calendar event attendee list.
 * 
 * Body: { eventId, email }
 * Returns: { success }
 */
exports.removeEventParticipant = functions
  .runWith({
    secrets: ['GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY'],
    timeoutSeconds: 15,
    memory: '256MB',
  })
  .https.onRequest((req, res) => {
    return cors(req, res, async () => {
      if (req.method === 'OPTIONS') {
        res.status(204).send('');
        return;
      }

      try {
        const { eventId, email } = req.body;

        if (!eventId || !email) {
          res.status(400).json({ error: 'Missing required fields: eventId, email' });
          return;
        }

        const db = admin.firestore();
        const eventDoc = await db.collection('events').doc(eventId).get();

        if (!eventDoc.exists) {
          res.status(404).json({ error: 'Event not found' });
          return;
        }

        const eventData = eventDoc.data();
        const calendarEventId = eventData.calendarEventId || '';

        if (calendarEventId && email) {
          try {
            await calendarService.removeAttendeeFromEvent(calendarEventId, email);
            console.log(`Removed ${email} from Calendar event ${calendarEventId}`);
          } catch (calError) {
            console.warn(`Failed to remove ${email} from Calendar event:`, calError.message);
          }
        }

        res.status(200).json({ success: true });

      } catch (error) {
        console.error('removeEventParticipant error:', error);
        res.status(500).json({
          error: 'Failed to remove participant from calendar',
          details: error.message,
        });
      }
    });
  });

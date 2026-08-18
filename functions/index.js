const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');
const cors = require('cors')({ origin: true });
const crypto = require('crypto');
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

// ── LinkedIn OAuth (existing) ────────────────────────────────────────────────

exports.exchangeLinkedInCode = functions.https.onRequest((req, res) => {
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

function getBadgeName(tier) {
  const badges = {
    'ASTEROID': 'Explorer',
    'MOON': 'Lunar Member',
    'EARTH': 'Earth Member',
    'SUN': 'Solar Elite'
  };
  return badges[tier] || tier;
}


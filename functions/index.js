const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');
const cors = require('cors')({ origin: true });
require('dotenv').config();

admin.initializeApp();

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

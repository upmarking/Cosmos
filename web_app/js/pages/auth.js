/* ============================================================
   Cosmos PWA — Auth Page (Login / Sign Up / Forgot Password)
   ============================================================ */

import {
  auth, signInWithEmailAndPassword, createUserWithEmailAndPassword,
  signOut, sendPasswordResetEmail, googleProvider, signInWithPopup, sendEmailVerification,
  db, doc, getDoc, setDoc, updateDoc, serverTimestamp, collection, addDoc
} from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

let currentView = 'login'; // login | signup | forgot

export async function renderAuth(outlet) {
  outlet.innerHTML = getAuthHTML(currentView);
  attachAuthListeners(outlet);
}

function getAuthHTML(view) {
  if (view === 'signup') return getSignUpHTML();
  if (view === 'forgot') return getForgotHTML();
  return getLoginHTML();
}

function getLoginHTML() {
  return `
    <div class="auth-page">
      <div class="auth-container">
        <div class="auth-header">
          <div class="auth-logo-wrap">
            <img src="icons/logo.webp" alt="Cosmos Logo" width="48" height="48" style="border-radius: 10px; margin: 0 auto; display: block; object-fit: cover;" />
          </div>
          <h1 class="auth-title">Welcome Back</h1>
          <p class="auth-subtitle">Sign in to continue building meaningful connections</p>
        </div>
        <div class="auth-card">
          <button class="google-btn" id="google-signin-btn" type="button">
            <svg width="20" height="20" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
            Continue with Google
          </button>
          <div class="auth-divider">or</div>
          <form id="login-form">
            <div class="form-group">
              <label class="form-label" for="login-email">Email</label>
              <input class="form-input" type="email" id="login-email" placeholder="your@email.com" required autocomplete="email" />
            </div>
            <div class="form-group">
              <label class="form-label" for="login-password">Password</label>
              <input class="form-input" type="password" id="login-password" placeholder="Enter your password" required autocomplete="current-password" />
            </div>
            <div style="text-align:right;margin-bottom:1rem;">
              <button type="button" class="btn btn-ghost btn-sm" id="forgot-link" style="font-size:0.8rem;color:var(--purple);">Forgot password?</button>
            </div>
            <button type="submit" class="btn btn-primary btn-full btn-lg" id="login-submit-btn">
              Sign In
            </button>
          </form>
        </div>
        <div class="auth-footer">
          Don't have an account? <a href="#" id="goto-signup">Sign Up</a>
        </div>
      </div>
    </div>
  `;
}

function getSignUpHTML() {
  return `
    <div class="auth-page">
      <div class="auth-container">
        <div class="auth-header">
          <div class="auth-logo-wrap">
            <img src="icons/logo.webp" alt="Cosmos Logo" width="48" height="48" style="border-radius: 10px; margin: 0 auto; display: block; object-fit: cover;" />
          </div>
          <h1 class="auth-title">Create Account</h1>
          <p class="auth-subtitle">Join a community of builders who value depth</p>
        </div>
        <div class="auth-card">
          <button class="google-btn" id="google-signin-btn" type="button">
            <svg width="20" height="20" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
            Continue with Google
          </button>
          <div class="auth-divider">or</div>
          <form id="signup-form">
            <div class="form-group">
              <label class="form-label" for="signup-name">Full Name</label>
              <input class="form-input" type="text" id="signup-name" placeholder="John Doe" required autocomplete="name" />
            </div>
            <div class="form-group">
              <label class="form-label" for="signup-email">Email</label>
              <input class="form-input" type="email" id="signup-email" placeholder="your@email.com" required autocomplete="email" />
            </div>
            <div class="form-group">
              <label class="form-label" for="signup-password">Password</label>
              <input class="form-input" type="password" id="signup-password" placeholder="Min 8 characters" required minlength="8" autocomplete="new-password" />
            </div>
            <button type="submit" class="btn btn-primary btn-full btn-lg" id="signup-submit-btn">
              Create Account
            </button>
          </form>
        </div>
        <div class="auth-footer">
          Already have an account? <a href="#" id="goto-login">Sign In</a>
        </div>
      </div>
    </div>
  `;
}

function getForgotHTML() {
  return `
    <div class="auth-page">
      <div class="auth-container">
        <div class="auth-header">
          <div class="auth-logo-wrap">
            <img src="icons/logo.webp" alt="Cosmos Logo" width="48" height="48" style="border-radius: 10px; margin: 0 auto; display: block; object-fit: cover;" />
          </div>
          <h1 class="auth-title">Reset Password</h1>
          <p class="auth-subtitle">Enter your email and we'll send a reset link</p>
        </div>
        <div class="auth-card">
          <form id="forgot-form">
            <div class="form-group">
              <label class="form-label" for="forgot-email">Email</label>
              <input class="form-input" type="email" id="forgot-email" placeholder="your@email.com" required autocomplete="email" />
            </div>
            <button type="submit" class="btn btn-primary btn-full btn-lg" id="forgot-submit-btn">
              Send Reset Link
            </button>
          </form>
        </div>
        <div class="auth-footer">
          Remember your password? <a href="#" id="goto-login">Sign In</a>
        </div>
      </div>
    </div>
  `;
}

function attachAuthListeners(outlet) {
  // Google sign-in
  const googleBtn = outlet.querySelector('#google-signin-btn');
  if (googleBtn) {
    googleBtn.addEventListener('click', async () => {
      try {
        googleBtn.disabled = true;
        googleBtn.textContent = 'Signing in...';
        const cred = await signInWithPopup(auth, googleProvider);
        if (!cred.user.emailVerified) {
          showToast('Please verify your email address to continue.', 'info');
          router.navigate('/verify-email');
          return;
        }
        showToast('Welcome to Cosmos!', 'success');
      } catch (err) {
        console.error('Google sign-in error:', err);
        showToast(err.message || 'Google sign-in failed', 'error');
        googleBtn.disabled = false;
        googleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg> Continue with Google`;
      }
    });
  }

  // Login form
  const loginForm = outlet.querySelector('#login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = outlet.querySelector('#login-email').value.trim();
      const password = outlet.querySelector('#login-password').value;
      const btn = outlet.querySelector('#login-submit-btn');
      try {
        btn.disabled = true;
        btn.textContent = 'Signing in...';
        const cred = await signInWithEmailAndPassword(auth, email, password);
        if (!cred.user.emailVerified) {
          showToast('Please verify your email address to continue.', 'info');
          router.navigate('/verify-email');
          return;
        }
        showToast('Welcome back!', 'success');
      } catch (err) {
        showToast(getAuthError(err.code), 'error');
        btn.disabled = false;
        btn.textContent = 'Sign In';
      }
    });
  }

  // Signup form
  const signupForm = outlet.querySelector('#signup-form');
  if (signupForm) {
    signupForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = outlet.querySelector('#signup-name').value.trim();
      const email = outlet.querySelector('#signup-email').value.trim();
      const password = outlet.querySelector('#signup-password').value;
      const btn = outlet.querySelector('#signup-submit-btn');
      try {
        btn.disabled = true;
        btn.textContent = 'Creating account...';
        const cred = await createUserWithEmailAndPassword(auth, email, password);
        
        // Send verification email
        await sendEmailVerification(cred.user);

        // Create user profile document with document ID as uid
        try {
          await setDoc(doc(db, 'users', cred.user.uid), {
            id: cred.user.uid,
            name: name,
            email: email.toLowerCase(),
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
            isProfileComplete: false,
            primaryUserType: '',
            headline: '',
            role: '',
            company: '',
            avatarUrl: '',
            location: '',
            bio: '',
            tags: [],
            goalStatement: '',
            longTermVision: '',
            lookingFor: [],
            isLinkedInConnected: false,
            membershipTier: 'EXPLORER',
            connectionsCount: 0,
            followersCount: 0,
            followingCount: 0,
            eventsAttended: 0,
            followUpsCompleted: 0,
            joinedCircles: [],
            pendingCircles: []
          });
        } catch (dbErr) {
          console.warn('Profile creation failed:', dbErr);
        }

        showToast('Account created! A verification link has been sent. Please verify your email to continue.', 'success');
        router.navigate('/verify-email');
      } catch (err) {
        showToast(getAuthError(err.code), 'error');
        btn.disabled = false;
        btn.textContent = 'Create Account';
      }
    });
  }

  // Forgot password form
  const forgotForm = outlet.querySelector('#forgot-form');
  if (forgotForm) {
    forgotForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = outlet.querySelector('#forgot-email').value.trim();
      const btn = outlet.querySelector('#forgot-submit-btn');
      try {
        btn.disabled = true;
        btn.textContent = 'Sending...';
        await sendPasswordResetEmail(auth, email);
        showToast('Reset link sent! Check your email.', 'success');
        currentView = 'login';
        renderAuth(outlet);
      } catch (err) {
        showToast(getAuthError(err.code), 'error');
        btn.disabled = false;
        btn.textContent = 'Send Reset Link';
      }
    });
  }

  // Navigation links
  const gotoSignup = outlet.querySelector('#goto-signup');
  if (gotoSignup) gotoSignup.addEventListener('click', (e) => { e.preventDefault(); currentView = 'signup'; renderAuth(outlet); });
  
  const gotoLogin = outlet.querySelector('#goto-login');
  if (gotoLogin) gotoLogin.addEventListener('click', (e) => { e.preventDefault(); currentView = 'login'; renderAuth(outlet); });
  
  const forgotLink = outlet.querySelector('#forgot-link');
  if (forgotLink) forgotLink.addEventListener('click', () => { currentView = 'forgot'; renderAuth(outlet); });
}

function getAuthError(code) {
  const errors = {
    'auth/invalid-email': 'Invalid email address',
    'auth/user-disabled': 'This account has been disabled',
    'auth/user-not-found': 'No account found with this email',
    'auth/wrong-password': 'Incorrect password',
    'auth/email-already-in-use': 'An account with this email already exists',
    'auth/weak-password': 'Password must be at least 8 characters',
    'auth/too-many-requests': 'Too many attempts. Please try again later.',
    'auth/invalid-credential': 'Invalid email or password',
    'auth/popup-closed-by-user': 'Sign-in popup was closed',
  };
  return errors[code] || 'Authentication failed. Please try again.';
}

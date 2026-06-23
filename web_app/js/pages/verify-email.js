/* ============================================================
   Cosmos PWA — Verify Email Page
   ============================================================ */

import { auth, signOut, sendEmailVerification } from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

export async function renderVerifyEmail(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  outlet.innerHTML = `
    <div class="auth-page">
      <div class="auth-container anim-fade-up">
        <div class="auth-header">
          <div class="auth-logo-wrap">
            <img src="icons/logo.webp" alt="Cosmos Logo" width="48" height="48" style="border-radius: 10px; margin: 0 auto; display: block; object-fit: cover;" />
          </div>
          <h1 class="auth-title">Verify Your Email</h1>
          <p class="auth-subtitle">We sent a verification link to <strong style="color:var(--purple);">${user.email}</strong>. Please check your inbox.</p>
        </div>
        
        <div class="auth-card" style="text-align: center;">
          <div style="font-size: 3rem; margin-bottom: 1.5rem; animation: pulse 2s infinite;">✉️</div>
          
          <button class="btn btn-primary btn-full btn-lg" id="btn-verified" type="button" style="margin-bottom: 1rem;">
            I have verified my email
          </button>
          
          <button class="btn btn-outline btn-full" id="btn-resend" type="button" style="margin-bottom: 1.5rem; border: 1px solid var(--purple-light); background: transparent; color: var(--purple);">
            Resend verification link
          </button>
          
          <div style="border-top: 1px solid var(--purple-light); padding-top: 1rem; margin-top: 1rem;">
            <button class="btn btn-ghost btn-sm" id="btn-signout" type="button" style="color: var(--text-muted); background: transparent; border: none; cursor: pointer; display: block; margin: 0 auto;">
              Cancel & Sign Out
            </button>
          </div>
        </div>
      </div>
    </div>
  `;

  // Attach event listeners
  const btnVerified = outlet.querySelector('#btn-verified');
  const btnResend = outlet.querySelector('#btn-resend');
  const btnSignout = outlet.querySelector('#btn-signout');

  btnVerified.addEventListener('click', async () => {
    try {
      btnVerified.disabled = true;
      btnVerified.textContent = 'Checking status...';
      
      // Reload Firebase User state to get latest emailVerified value
      await auth.currentUser.reload();
      const updatedUser = auth.currentUser;
      
      if (updatedUser.emailVerified) {
        showToast('Email verified successfully! Welcome to Cosmos.', 'success');
        router.navigate('/connect');
      } else {
        showToast('Email address is still not verified. Please check your email and click the verification link.', 'error');
        btnVerified.disabled = false;
        btnVerified.textContent = 'I have verified my email';
      }
    } catch (err) {
      console.error(err);
      showToast(err.message || 'Verification check failed. Please try again.', 'error');
      btnVerified.disabled = false;
      btnVerified.textContent = 'I have verified my email';
    }
  });

  btnResend.addEventListener('click', async () => {
    try {
      btnResend.disabled = true;
      btnResend.textContent = 'Sending...';
      await sendEmailVerification(auth.currentUser);
      showToast('Verification email resent! Please check your inbox.', 'success');
    } catch (err) {
      console.error(err);
      showToast(err.message || 'Failed to resend verification email.', 'error');
    } finally {
      btnResend.disabled = false;
      btnResend.textContent = 'Resend verification link';
    }
  });

  btnSignout.addEventListener('click', async () => {
    try {
      btnSignout.disabled = true;
      await signOut(auth);
      showToast('Signed out successfully.', 'success');
      router.navigate('/auth');
    } catch (err) {
      console.error(err);
      showToast('Failed to sign out. Please refresh.', 'error');
      btnSignout.disabled = false;
    }
  });
}

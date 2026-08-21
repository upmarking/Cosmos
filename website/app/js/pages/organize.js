/* ============================================================
   Cosmos PWA — Organize Page (Events & Networking Sessions)
   Maps to the 'O' in COSMOS navigation
   ============================================================ */

import { auth, db, collection, query, onSnapshot, doc, getDoc, getDocs, setDoc, updateDoc, addDoc, increment, serverTimestamp, storage, ref, uploadBytes, getDownloadURL } from '../firebase-config.js';
import { showToast } from '../app.js';

const tabs = ['All Events', 'Speed Networking', 'Curated Meetup', 'Invite Only', 'Industry Round'];
let activeTab = 'All Events';
let unsubEvents = null;
const registrationsMap = new Map();
const hostProfileMap = new Map();
let eventsList = [];

export async function renderOrganize(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  if (unsubEvents) {
    unsubEvents();
    unsubEvents = null;
  }

  outlet.innerHTML = `
    <div class="events-page page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="page-title">Organize</h1>
          <p class="page-subtitle">Events & structured networking sessions</p>
        </div>
        <button class="btn btn-primary btn-sm" id="btn-create-event">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Create Event
        </button>
      </div>
      
      <div class="events-tabs-container">
        <div class="events-tabs" id="events-tabs">
          ${tabs.map(t => `
            <button class="event-tab ${t === activeTab ? 'active' : ''}" data-tab="${t}">${t}</button>
          `).join('')}
        </div>
      </div>

      <!-- Dashboard Layout Grid -->
      <div class="organize-dashboard-grid">
        <!-- Your Events Dashboard Card -->
        <div class="organize-card" id="your-events-section">
          <div class="organize-card-header">
            <h3 class="organize-card-title">Your Events</h3>
            <p class="organize-card-subtitle">Sessions you are hosting or attending</p>
          </div>
          <div class="your-events-container" id="your-events-container">
            <div class="loading-spinner" style="margin:1rem auto; display:block;"></div>
          </div>
        </div>

        <!-- Picked for You Dashboard Card -->
        <div class="organize-card explore-events-section">
          <div class="organize-card-header">
            <h3 class="organize-card-title">Picked for You</h3>
            <p class="organize-card-subtitle">Recommended networking events and meetups</p>
          </div>
          <div class="events-timeline stagger" id="events-timeline">
            <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
          </div>
        </div>
      </div>

    </div>
  `;

  // Append Create Event Modal dynamically to body so it escapes stacking context
  let modal = document.getElementById('create-event-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.className = 'modal-overlay hidden';
    modal.id = 'create-event-modal';
    // Set minimal inline styling for safety, letting CSS classes handle transitions and layout
    Object.assign(modal.style, {
      zIndex: '99999'
    });
    document.body.appendChild(modal);
  }

  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <div class="modal-header-top" style="display:flex; justify-content:space-between; align-items:center; width:100%;">
          <h3>Create New Event</h3>
          <button class="modal-close" id="btn-close-event-modal" aria-label="Close modal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        
        <div class="form-steps-indicator">
          <div class="step-indicator-item active" data-step-indicator="1">
            <div class="step-num">1</div>
            <span class="step-label">Details</span>
          </div>
          <div class="step-line"></div>
          <div class="step-indicator-item" data-step-indicator="2">
            <div class="step-num">2</div>
            <span class="step-label">Schedule</span>
          </div>
          <div class="step-line"></div>
          <div class="step-indicator-item" data-step-indicator="3">
            <div class="step-num">3</div>
            <span class="step-label">Cover & Price</span>
          </div>
        </div>
      </div>
      <div class="modal-body">
        <form id="create-event-form">
          <!-- Step 1: Details -->
          <div class="form-step-pane active" data-step-pane="1">
            <div class="form-group">
              <label class="form-label" for="event-title">Event Title</label>
              <input class="form-input" type="text" id="event-title" required placeholder="e.g. Founders Speed Network" />
            </div>
            
            <div class="form-group">
              <label class="form-label" for="event-desc">Description</label>
              <textarea class="form-input" id="event-desc" required placeholder="What is this event about? (e.g. format, networking details)" style="min-height:90px;"></textarea>
            </div>

            <div style="display: grid; grid-template-columns: 1.2fr 1fr; gap: 0.75rem;">
              <div class="form-group">
                <label class="form-label" for="event-type">Event Type</label>
                <select class="form-input" id="event-type" required>
                  <option value="OPEN_NETWORKING">Open Networking</option>
                  <option value="SPEED_NETWORKING">Speed Networking</option>
                  <option value="CURATED_MEETUP">Curated Meetup</option>
                  <option value="INVITE_ONLY">Invite Only</option>
                  <option value="INDUSTRY_ROUND">Industry Round</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label" for="event-max-participants">Max Spots</label>
                <input class="form-input" type="number" id="event-max-participants" min="2" max="1000" value="50" required />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label" for="event-tags">Tags (comma-separated)</label>
              <input class="form-input" type="text" id="event-tags" placeholder="e.g. AI, B2B, Founders" />
            </div>
          </div>
          
          <!-- Step 2: Schedule -->
          <div class="form-step-pane hidden" data-step-pane="2">
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem;">
              <div class="form-group">
                <label class="form-label" for="event-date">Date</label>
                <input class="form-input" type="date" id="event-date" required />
              </div>
              <div class="form-group">
                <label class="form-label" for="event-time">Time</label>
                <input class="form-input" type="time" id="event-time" required />
              </div>
            </div>
            
            <div class="form-group" style="position:relative;">
              <label class="form-label" for="event-location">Location</label>
              <input class="form-input" type="text" id="event-location" required placeholder="e.g. San Francisco, CA or Zoom Link" autocomplete="off" />
              <div id="location-suggestions" style="position:absolute; top:100%; left:0; right:0; z-index:1000; display:none; max-height:200px; overflow-y:auto; padding:0.5rem; background:#0c1020; border:1px solid var(--border); border-radius:8px; box-shadow: 0 8px 32px rgba(0,0,0,0.5);">
              </div>
              <div id="location-map-preview" style="margin-top: 0.5rem; border-radius: 12px; overflow: hidden; height: 180px; display: none; border: 1px solid var(--border);">
                <iframe id="location-map-iframe" width="100%" height="100%" frameborder="0" style="border:0" allowfullscreen></iframe>
              </div>
            </div>
          </div>
          
          <!-- Step 3: Cover & Price -->
          <div class="form-step-pane hidden" data-step-pane="3">
            <div class="switch-group" style="margin-top:0.5rem; margin-bottom:1rem;">
              <div class="switch-label-wrap">
                <span class="switch-title">Paid Event</span>
                <span class="switch-description">Charge participants to attend</span>
              </div>
              <label class="switch">
                <input type="checkbox" id="event-is-paid" />
                <span class="slider"></span>
              </label>
            </div>

            <div class="form-group hidden" id="price-group">
              <label class="form-label" for="event-price">Ticket Price</label>
              <input class="form-input" type="text" id="event-price" placeholder="e.g. $10" />
            </div>

            <div class="form-group" style="margin-top:1rem;">
              <label class="form-label" for="event-image">Event Cover Image</label>
              <div class="image-upload-wrapper" style="position:relative; display:flex; flex-direction:column; align-items:center; justify-content:center; border:2px dashed var(--border); border-radius:12px; padding:1.5rem; text-align:center; cursor:pointer; background:var(--bg-input); transition:all 0.2s ease;">
                <input type="file" id="event-image" accept="image/*" style="position:absolute; inset:0; opacity:0; cursor:pointer; z-index:10;" />
                <div class="upload-placeholder" id="upload-placeholder" style="display:flex; flex-direction:column; align-items:center; gap:0.5rem; color:var(--text-secondary);">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                    <circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21 15 16 10 5 21"/>
                  </svg>
                  <span style="font-size:0.85rem; font-weight:500;">Click or drag to upload image</span>
                  <span style="font-size:0.75rem; color:var(--text-muted);">PNG, JPG or WebP (max 5MB)</span>
                </div>
                <div class="upload-preview" id="upload-preview" style="width:100%; position:relative; display:none; z-index:20;">
                  <div class="preview-crop-container" style="position:relative; width:100%; aspect-ratio:2 / 1; overflow:hidden; border-radius:8px; border:1px solid var(--border);">
                    <img id="preview-img" src="" style="width:100%; height:100%; object-fit:cover; position:absolute; left:0; top:0; transform-origin:center center; transition:none;" />
                  </div>
                  <button type="button" class="btn-remove-image" id="btn-remove-image" style="position:absolute; top:8px; right:8px; background:rgba(0,0,0,0.6); border-radius:50%; width:28px; height:28px; display:flex; align-items:center; justify-content:center; color:#fff; border:1px solid rgba(255,255,255,0.2); cursor:pointer; z-index:30;">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                  </button>

                  <div class="image-adjust-controls" style="margin-top: 1rem; display:flex; flex-direction:column; gap:0.75rem; text-align:left; background:rgba(255,255,255,0.02); padding:0.75rem; border-radius:8px; border:1px solid var(--border);">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                      <span style="font-size:0.75rem; font-weight:600; color:var(--text-secondary);">Zoom</span>
                      <span id="zoom-val" style="font-size:0.75rem; color:var(--purple); font-weight:700;">100%</span>
                    </div>
                    <input type="range" id="cover-zoom" min="100" max="300" value="100" class="form-input-range" style="width:100%; accent-color:var(--purple); cursor:pointer;" />
                    
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-top:0.25rem;">
                      <span style="font-size:0.75rem; font-weight:600; color:var(--text-secondary);">Reposition (Vertical Offset)</span>
                      <span id="pan-val" style="font-size:0.75rem; color:var(--purple); font-weight:700;">0px</span>
                    </div>
                    <input type="range" id="cover-pan-y" min="-80" max="80" value="0" class="form-input-range" style="width:100%; accent-color:var(--purple); cursor:pointer;" />
                  </div>
                </div>
              </div>
            </div>

            <div class="form-group" style="margin-top:1rem;">
              <label class="form-label">Or Select Default Cover Theme</label>
              <div class="default-covers-grid">
                <div class="default-cover-option active" data-gradient="gradient:cosmos-glow" style="background:linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #581c87 100%);" title="Cosmos Glow"></div>
                <div class="default-cover-option" data-gradient="gradient:sunset-aurora" style="background:linear-gradient(135deg, #1e1b4b 0%, #701a75 50%, #f43f5e 100%);" title="Sunset Aurora"></div>
                <div class="default-cover-option" data-gradient="gradient:cyber-neon" style="background:linear-gradient(135deg, #020617 0%, #0f766e 60%, #06b6d4 100%);" title="Cyber Neon"></div>
                <div class="default-cover-option" data-gradient="gradient:deep-space" style="background:linear-gradient(135deg, #030712 0%, #1e1b4b 40%, #db2777 100%);" title="Deep Space"></div>
                <div class="default-cover-option" data-gradient="gradient:emerald-matrix" style="background:linear-gradient(135deg, #022c22 0%, #065f46 50%, #10b981 100%);" title="Emerald Matrix"></div>
              </div>
            </div>
          </div>
        </form>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary btn-sm" id="btn-prev-step" type="button" style="display:none;">Back</button>
        <button class="btn btn-secondary btn-sm" id="btn-cancel-event" type="button">Cancel</button>
        <button class="btn btn-primary btn-sm" id="btn-next-step" type="button">Next</button>
        <button class="btn btn-primary btn-sm hidden" id="btn-submit-event" type="submit" form="create-event-form">Post Event</button>
      </div>
    </div>
  `;

  // Modal DOM elements
  const btnCreate = outlet.querySelector('#btn-create-event');
  const btnClose = modal.querySelector('#btn-close-event-modal');
  const btnCancel = modal.querySelector('#btn-cancel-event');
  const checkboxIsPaid = modal.querySelector('#event-is-paid');
  const priceGroup = modal.querySelector('#price-group');
  const createForm = modal.querySelector('#create-event-form');
  const btnSubmit = modal.querySelector('#btn-submit-event');

  const btnPrev = modal.querySelector('#btn-prev-step');
  const btnNext = modal.querySelector('#btn-next-step');

  let currentStep = 1;
  const setStep = (step) => {
    currentStep = step;
    
    // Update step indicator classes
    modal.querySelectorAll('.step-indicator-item').forEach(el => {
      const s = parseInt(el.dataset.stepIndicator);
      el.classList.toggle('active', s === currentStep);
      el.classList.toggle('completed', s < currentStep);
    });

    // Update panes
    modal.querySelectorAll('.form-step-pane').forEach(el => {
      const s = parseInt(el.dataset.stepPane);
      if (s === currentStep) {
        el.classList.remove('hidden');
        el.classList.add('active');
      } else {
        el.classList.add('hidden');
        el.classList.remove('active');
      }
    });

    // Update buttons in footer
    if (currentStep === 1) {
      btnPrev.style.display = 'none';
      btnCancel.style.display = 'inline-flex';
      btnNext.style.display = 'inline-flex';
      btnSubmit.classList.add('hidden');
    } else {
      btnPrev.style.display = 'inline-flex';
      btnCancel.style.display = 'none';
      if (currentStep === 3) {
        btnNext.style.display = 'none';
        btnSubmit.classList.remove('hidden');
      } else {
        btnNext.style.display = 'inline-flex';
        btnSubmit.classList.add('hidden');
      }
    }
  };

  const validateStep = (step) => {
    if (step === 1) {
      const title = modal.querySelector('#event-title').value.trim();
      const desc = modal.querySelector('#event-desc').value.trim();
      const maxSpots = parseInt(modal.querySelector('#event-max-participants').value) || 0;
      if (!title) {
        showToast('Please enter an event title', 'error');
        return false;
      }
      if (!desc) {
        showToast('Please enter an event description', 'error');
        return false;
      }
      if (maxSpots < 2) {
        showToast('Max spots must be at least 2', 'error');
        return false;
      }
      return true;
    }
    if (step === 2) {
      const date = modal.querySelector('#event-date').value;
      const time = modal.querySelector('#event-time').value;
      const location = modal.querySelector('#event-location').value.trim();
      if (!date) {
        showToast('Please select a date', 'error');
        return false;
      }
      if (!time) {
        showToast('Please select a time', 'error');
        return false;
      }
      if (!location) {
        showToast('Please enter a location or link', 'error');
        return false;
      }
      return true;
    }
    if (step === 3) {
      const isPaid = modal.querySelector('#event-is-paid').checked;
      const price = modal.querySelector('#event-price').value.trim();
      if (isPaid && !price) {
        showToast('Please enter a ticket price', 'error');
        return false;
      }
      return true;
    }
    return true;
  };

  btnNext.addEventListener('click', () => {
    if (validateStep(currentStep)) {
      setStep(currentStep + 1);
    }
  });

  btnPrev.addEventListener('click', () => {
    setStep(currentStep - 1);
  });

  // Toggle modal visibility
  const openModal = () => {
    try {
      modal.classList.remove('hidden');
      // Trigger a reflow to start the transition
      modal.offsetHeight;
      modal.classList.add('active');
      document.body.style.overflow = 'hidden'; // Disable scroll under modal
      setStep(1);
    } catch (err) {
      console.error('[Cosmos] Error opening modal:', err);
    }
  };

  const closeModal = () => {
    modal.classList.remove('active');
    // Wait for transition to complete before hiding modal overlay
    setTimeout(() => {
      if (!modal.classList.contains('active')) {
        modal.classList.add('hidden');
      }
    }, 300);
    document.body.style.overflow = '';
    createForm.reset();
    priceGroup.classList.add('hidden');
    setStep(1);
    
    // Reset image upload preview
    selectedImageFile = null;
    const uploadPreview = modal.querySelector('#upload-preview');
    const uploadPlaceholder = modal.querySelector('#upload-placeholder');
    const previewImg = modal.querySelector('#preview-img');
    const inputImage = modal.querySelector('#event-image');
    if (uploadPreview) uploadPreview.style.display = 'none';
    if (uploadPlaceholder) uploadPlaceholder.style.display = 'flex';
    if (previewImg) {
      previewImg.src = '';
      previewImg.style.transform = 'scale(1) translateY(0px)';
    }
    if (inputImage) inputImage.value = '';

    // Reset default covers selector
    selectedDefaultGradient = 'gradient:cosmos-glow';
    const defaultCovers = modal.querySelectorAll('.default-cover-option');
    defaultCovers.forEach(o => {
      o.classList.toggle('active', o.dataset.gradient === 'gradient:cosmos-glow');
    });

    const coverZoom = modal.querySelector('#cover-zoom');
    const coverPanY = modal.querySelector('#cover-pan-y');
    const zoomVal = modal.querySelector('#zoom-val');
    const panVal = modal.querySelector('#pan-val');
    if (coverZoom) coverZoom.value = 100;
    if (coverPanY) coverPanY.value = 0;
    if (zoomVal) zoomVal.textContent = '100%';
    if (panVal) panVal.textContent = '0px';

    const mapPreview = modal.querySelector('#location-map-preview');
    const mapIframe = modal.querySelector('#location-map-iframe');
    const suggestionsBox = modal.querySelector('#location-suggestions');
    if (mapPreview) mapPreview.style.display = 'none';
    if (mapIframe) mapIframe.src = '';
    if (suggestionsBox) {
      suggestionsBox.style.display = 'none';
      suggestionsBox.innerHTML = '';
    }
  };
  if (btnCreate) btnCreate.addEventListener('click', openModal);
  btnClose.addEventListener('click', closeModal);
  btnCancel.addEventListener('click', closeModal);

  // Close modal when clicking backdrop
  modal.addEventListener('click', (e) => {
    if (e.target === modal) {
      closeModal();
    }
  });

  // Image upload preview listener
  const inputImage = modal.querySelector('#event-image');
  const uploadPlaceholder = modal.querySelector('#upload-placeholder');
  const uploadPreview = modal.querySelector('#upload-preview');
  const previewImg = modal.querySelector('#preview-img');
  const btnRemoveImage = modal.querySelector('#btn-remove-image');
  const uploadWrapper = modal.querySelector('.image-upload-wrapper');
  let selectedImageFile = null;

  const coverZoom = modal.querySelector('#cover-zoom');
  const coverPanY = modal.querySelector('#cover-pan-y');
  const zoomVal = modal.querySelector('#zoom-val');
  const panVal = modal.querySelector('#pan-val');

  function updateImageAdjustment() {
    if (!previewImg) return;
    const zoom = parseFloat(coverZoom.value) / 100;
    const panY = parseInt(coverPanY.value);
    previewImg.style.transform = `scale(${zoom}) translateY(${panY}px)`;
    if (zoomVal) zoomVal.textContent = `${coverZoom.value}%`;
    if (panVal) panVal.textContent = `${panY}px`;
  }

  function resetImageAdjustment() {
    if (coverZoom) coverZoom.value = 100;
    if (coverPanY) coverPanY.value = 0;
    if (previewImg) previewImg.style.transform = 'scale(1) translateY(0px)';
    if (zoomVal) zoomVal.textContent = '100%';
    if (panVal) panVal.textContent = '0px';
  }

  if (coverZoom) coverZoom.addEventListener('input', updateImageAdjustment);
  if (coverPanY) coverPanY.addEventListener('input', updateImageAdjustment);

  let selectedDefaultGradient = 'gradient:cosmos-glow';
  const defaultCovers = modal.querySelectorAll('.default-cover-option');
  defaultCovers.forEach(option => {
    option.addEventListener('click', () => {
      selectedDefaultGradient = option.dataset.gradient;
      defaultCovers.forEach(o => {
        o.classList.toggle('active', o === option);
      });
      // Clear custom image if default cover is selected
      selectedImageFile = null;
      inputImage.value = '';
      previewImg.src = '';
      resetImageAdjustment();
      uploadPreview.style.display = 'none';
      uploadPlaceholder.style.display = 'flex';
    });
  });

  inputImage.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        showToast('Image size should be less than 5MB', 'error');
        inputImage.value = '';
        return;
      }
      selectedImageFile = file;
      resetImageAdjustment();
      // Remove default cover highlights when a custom image is uploaded
      defaultCovers.forEach(o => o.classList.remove('active'));
      const reader = new FileReader();
      reader.onload = (event) => {
        previewImg.src = event.target.result;
        uploadPlaceholder.style.display = 'none';
        uploadPreview.style.display = 'block';
      };
      reader.readAsDataURL(file);
    }
  });

  btnRemoveImage.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    selectedImageFile = null;
    inputImage.value = '';
    previewImg.src = '';
    resetImageAdjustment();
    uploadPreview.style.display = 'none';
    uploadPlaceholder.style.display = 'flex';
    // Restore default cover highlights when custom image is removed
    defaultCovers.forEach(o => {
      o.classList.toggle('active', o.dataset.gradient === selectedDefaultGradient);
    });
  });

  // Drag and drop effects
  ['dragenter', 'dragover'].forEach(eventName => {
    uploadWrapper.addEventListener(eventName, (e) => {
      e.preventDefault();
      uploadWrapper.style.borderColor = 'var(--purple)';
      uploadWrapper.style.background = 'rgba(167,139,250,0.05)';
    }, false);
  });

  ['dragleave', 'drop'].forEach(eventName => {
    uploadWrapper.addEventListener(eventName, (e) => {
      e.preventDefault();
      uploadWrapper.style.borderColor = 'var(--border)';
      uploadWrapper.style.background = 'var(--bg-input)';
    }, false);
  });

  uploadWrapper.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const file = dt.files[0];
    if (file && file.type.startsWith('image/')) {
      if (file.size > 5 * 1024 * 1024) {
        showToast('Image size should be less than 5MB', 'error');
        return;
      }
      selectedImageFile = file;
      resetImageAdjustment();
      defaultCovers.forEach(o => o.style.borderColor = 'transparent');
      const reader = new FileReader();
      reader.onload = (event) => {
        previewImg.src = event.target.result;
        uploadPlaceholder.style.display = 'none';
        uploadPreview.style.display = 'block';
      };
      reader.readAsDataURL(file);
    }
  });

  // Toggle ticket price input
  checkboxIsPaid.addEventListener('change', (e) => {
    if (e.target.checked) {
      priceGroup.classList.remove('hidden');
      modal.querySelector('#event-price').setAttribute('required', 'true');
    } else {
      priceGroup.classList.add('hidden');
      modal.querySelector('#event-price').removeAttribute('required');
      modal.querySelector('#event-price').value = '';
    }
  });

  // Google Maps and autocomplete preview listener
  const inputLocation = modal.querySelector('#event-location');
  const mapPreview = modal.querySelector('#location-map-preview');
  const mapIframe = modal.querySelector('#location-map-iframe');
  const suggestionsBox = modal.querySelector('#location-suggestions');
  let debounceTimeout = null;

  inputLocation.addEventListener('input', (e) => {
    const loc = e.target.value.trim();

    if (debounceTimeout) clearTimeout(debounceTimeout);

    if (!loc) {
      suggestionsBox.style.display = 'none';
      mapPreview.style.display = 'none';
      mapIframe.src = '';
      return;
    }

    if (!loc.toLowerCase().includes('zoom') && !loc.toLowerCase().includes('meet.google') && !loc.toLowerCase().includes('http')) {
      mapPreview.style.display = 'block';
      mapIframe.src = `https://maps.google.com/maps?q=${encodeURIComponent(loc)}&t=&z=13&ie=UTF8&iwloc=&output=embed`;
    } else {
      mapPreview.style.display = 'none';
      mapIframe.src = '';
    }

    if (loc.length > 2 && !loc.toLowerCase().includes('zoom') && !loc.toLowerCase().includes('http')) {
      debounceTimeout = setTimeout(async () => {
        try {
          const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(loc)}&limit=5&email=contact@cosmos.app`);
          const data = await res.json();
          if (data && data.length > 0) {
            suggestionsBox.innerHTML = data.map(item => `
              <div class="suggestion-item" style="padding:0.6rem 0.5rem; cursor:pointer; font-size:0.82rem; color:var(--text-primary); border-bottom:1px solid var(--border);" data-address="${item.display_name}">
                ${item.display_name}
              </div>
            `).join('');
            suggestionsBox.style.display = 'block';

            // Hover effect in JavaScript for simple styling
            suggestionsBox.querySelectorAll('.suggestion-item').forEach(el => {
              el.addEventListener('mouseenter', () => el.style.background = 'rgba(255,255,255,0.06)');
              el.addEventListener('mouseleave', () => el.style.background = 'transparent');
              el.addEventListener('click', () => {
                const addr = el.dataset.address;
                inputLocation.value = addr;
                suggestionsBox.style.display = 'none';
                mapPreview.style.display = 'block';
                mapIframe.src = `https://maps.google.com/maps?q=${encodeURIComponent(addr)}&t=&z=13&ie=UTF8&iwloc=&output=embed`;
              });
            });
          } else {
            suggestionsBox.style.display = 'none';
          }
        } catch (err) {
          console.error('[Cosmos] Autocomplete suggestions fetch failed:', err);
        }
      }, 500);
    } else {
      suggestionsBox.style.display = 'none';
    }
  });

  // Helpers to format dates and times beautifully
  function formatEventDate(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length !== 3) return dateStr;
    const date = new Date(parts[0], parts[1] - 1, parts[2]);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  function formatEventTime(timeStr) {
    if (!timeStr) return '';
    const parts = timeStr.split(':');
    if (parts.length !== 2) return timeStr;
    let hours = parseInt(parts[0]);
    const minutes = parts[1];
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12;
    return `${hours}:${minutes} ${ampm} IST`; // Matches existing events format
  }

  // Handle Event form submit
  createForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const title = modal.querySelector('#event-title').value.trim();
    const description = modal.querySelector('#event-desc').value.trim();
    const rawDate = modal.querySelector('#event-date').value;
    const rawTime = modal.querySelector('#event-time').value;
    const location = modal.querySelector('#event-location').value.trim();
    const type = modal.querySelector('#event-type').value;
    const maxParticipants = parseInt(modal.querySelector('#event-max-participants').value) || 50;
    const isPaid = checkboxIsPaid.checked;
    const price = isPaid ? modal.querySelector('#event-price').value.trim() : '';
    const tagsInput = modal.querySelector('#event-tags').value;
    const tags = tagsInput ? tagsInput.split(',').map(t => t.trim()).filter(Boolean) : ['Networking'];

    // Validation
    if (!title || !description || !rawDate || !rawTime || !location) {
      showToast('Please fill in all required fields.', 'error');
      return;
    }

    if (isPaid && !price) {
      showToast('Please enter a price for a paid event.', 'error');
      return;
    }

    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Posting...';

    try {
      let coverUrl = selectedDefaultGradient;
      if (selectedImageFile) {
        btnSubmit.textContent = 'Processing Image...';
        const zoomPercent = parseFloat(coverZoom.value);
        const offsetYPx = parseInt(coverPanY.value);
        const previewHeight = previewImg.parentElement.clientHeight || 200;
        
        const croppedBlob = await cropImage(previewImg.src, zoomPercent, offsetYPx, previewHeight);
        
        btnSubmit.textContent = 'Uploading Image...';
        const storageRef = ref(storage, `events/${Date.now()}_cropped.jpg`);
        const uploadResult = await uploadBytes(storageRef, croppedBlob);
        coverUrl = await getDownloadURL(uploadResult.ref);
      }

      const eventData = {
        title,
        description,
        date: formatEventDate(rawDate),
        time: formatEventTime(rawTime),
        location,
        type,
        participantCount: 0,
        maxParticipants,
        isPaid,
        price,
        coverUrl,
        tags,
        createdBy: user.uid,
        createdAt: serverTimestamp()
      };

      await addDoc(collection(db, 'events'), eventData);
      
      showToast('Event posted successfully! 📅', 'success');
      selectedImageFile = null;
      closeModal();
    } catch (err) {
      console.error('[Cosmos Organize] Create event error:', err);
      showToast('Failed to create event: ' + err.message, 'error');
    } finally {
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Post Event';
    }
  });

  // Tab clicks
  outlet.querySelectorAll('.event-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      activeTab = tab.dataset.tab;
      outlet.querySelectorAll('.event-tab').forEach(t => t.classList.toggle('active', t === tab));
      updateEventsDisplay(outlet, user.uid);
    });
  });

  triggerEventsFetch(outlet, user.uid);

  return () => {
    if (unsubEvents) {
      unsubEvents();
      unsubEvents = null;
    }
    const createModal = document.getElementById('create-event-modal');
    if (createModal) createModal.remove();
    const detailsModal = document.getElementById('event-details-modal');
    if (detailsModal) detailsModal.remove();
  };
}

function parseEventDate(dateStr) {
  if (!dateStr) return null;
  const cleanDate = dateStr
    .replace(/(Today|Tomorrow|Next|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*/gi, '')
    .trim();
  const d = new Date(cleanDate);
  return isNaN(d.getTime()) ? null : d;
}

function groupEventsByDay(events) {
  const groups = {};
  events.forEach(event => {
    const d = parseEventDate(event.date);
    if (!d) return;

    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const key = `${year}-${month}-${day}`;

    if (!groups[key]) {
      groups[key] = {
        date: d,
        events: []
      };
    }
    groups[key].events.push(event);
  });

  return Object.keys(groups)
    .sort()
    .map(key => groups[key]);
}

function getDayHeaderLabel(d) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const eventDate = new Date(d);
  eventDate.setHours(0, 0, 0, 0);

  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  if (eventDate.getTime() === today.getTime()) {
    return `Today / ${days[d.getDay()]}`;
  } else if (eventDate.getTime() === tomorrow.getTime()) {
    return `Tomorrow / ${days[d.getDay()]}`;
  } else {
    return `${months[d.getMonth()]} ${d.getDate()} / ${days[d.getDay()]}`;
  }
}

function updateEventsDisplay(outlet, currentUserId) {
  const yourEventsContainer = outlet.querySelector('#your-events-container');
  const timeline = outlet.querySelector('#events-timeline');
  if (!yourEventsContainer || !timeline) return;

  const filtered = getFilteredEvents();

  // 1. Render Your Events (both registered and hosted)
  const registered = eventsList.filter(e => registrationsMap.get(e.id) === true || (currentUserId && e.createdBy === currentUserId));
  if (registered.length === 0) {
    yourEventsContainer.innerHTML = `
      <div class="your-events-empty-card">
        <div class="your-events-empty-icon">🎟️</div>
        <div class="your-events-empty-info">
          <h4>No Upcoming Events</h4>
          <p>Events you are going to or hosting will show up here.</p>
        </div>
      </div>
    `;
  } else {
    yourEventsContainer.innerHTML = renderLumaEventCards(registered);
  }

  // 2. Render Upcoming Timeline (grouped by day)
  if (filtered.length === 0) {
    timeline.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon">📅</div>
        <h3 class="empty-state-title">No Events Found</h3>
        <p class="empty-state-desc">No upcoming events in this category. Check back soon!</p>
      </div>
    `;
  } else {
    const dayGroups = groupEventsByDay(filtered);
    timeline.innerHTML = dayGroups.map(group => {
      const label = getDayHeaderLabel(group.date);
      
      const rowsHtml = group.events.map(event => {
        const cardHtml = renderLumaEventCards([event]);
        return `
          <div class="timeline-event-row" data-id="${event.id}">
            <div class="timeline-time-col">
              <span class="timeline-time-text">${event.time.replace(/\s*IST\s*$/i, '')}</span>
              <span class="timeline-timezone-text">IST</span>
            </div>
            <div class="timeline-node-col">
              <div class="timeline-node-line"></div>
              <div class="timeline-node-dot"></div>
            </div>
            <div class="timeline-card-col">
              ${cardHtml}
            </div>
          </div>
        `;
      }).join('');

      return `
        <div class="timeline-day-group">
          <div class="timeline-day-header">${label}</div>
          <div class="timeline-day-list">
            ${rowsHtml}
          </div>
        </div>
      `;
    }).join('');
  }

  // Resolve creators names and avatars asynchronously
  resolveHostProfiles(outlet);

  // Attach card listeners
  attachEventCardListeners(outlet, currentUserId);
}

function getFilteredEvents() {
  if (activeTab === 'All Events') return eventsList;
  const tabMap = {
    'Speed Networking': 'SPEED_NETWORKING',
    'Curated Meetup': 'CURATED_MEETUP',
    'Invite Only': 'INVITE_ONLY',
    'Industry Round': 'INDUSTRY_ROUND'
  };
  const typeKey = tabMap[activeTab] || activeTab;
  return eventsList.filter(e => e.type === typeKey);
}

function triggerEventsFetch(outlet, currentUserId) {
  if (unsubEvents) {
    unsubEvents();
  }

  unsubEvents = onSnapshot(collection(db, 'events'), async (snapshot) => {
    eventsList = [];
    const checkRegPromises = [];

    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      const eventId = docSnap.id;
      const dateStr = data.date || '';

      const d = parseEventDate(dateStr);
      if (d) {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (d < today) return;
      }

      // Parallel check if current user is registered
      const promise = getDoc(doc(db, 'events', eventId, 'registrants', currentUserId))
        .then(regSnap => {
          registrationsMap.set(eventId, regSnap.exists());
        })
        .catch(err => {
          console.error('[Cosmos Organize] Registration check failed:', eventId, err);
          registrationsMap.set(eventId, false);
        });

      checkRegPromises.push(promise);

      eventsList.push({
        id: eventId,
        title: data.title || 'Unnamed Event',
        description: data.description || '',
        date: data.date || '',
        time: data.time || '',
        location: data.location || '',
        type: data.type || 'OPEN_NETWORKING',
        participantCount: data.participantCount || 0,
        maxParticipants: data.maxParticipants || 100,
        isPaid: data.isPaid || false,
        price: data.price || '',
        coverUrl: data.coverUrl || '',
        tags: data.tags || [],
        createdBy: data.createdBy || '',
        createdAt: data.createdAt
      });
    });

    await Promise.all(checkRegPromises);

    // Sort events list chronologically
    eventsList.sort((a, b) => {
      const da = parseEventDate(a.date) || new Date(8640000000000000);
      const dbDate = parseEventDate(b.date) || new Date(8640000000000000);
      return da - dbDate;
    });

    updateEventsDisplay(outlet, currentUserId);
  }, (error) => {
    console.error('[Cosmos Organize] Error listening to events:', error);
    const timeline = outlet.querySelector('#events-timeline');
    if (timeline) {
      timeline.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load events: ${error.message}</div>`;
    }
  });
}

function renderLumaEventCards(events) {
  return events.map(event => {
    const isJoined = registrationsMap.get(event.id) || false;
    const initial = event.title.charAt(0);

    const priceBadge = event.isPaid 
      ? `<span class="luma-price-badge paid">${event.price || 'Paid'}</span>`
      : `<span class="luma-price-badge free">Free</span>`;

    const tagsHtml = (event.tags || []).slice(0, 3).map(tag => `
      <span class="luma-card-tag">${tag}</span>
    `).join('');

    return `
      <div class="luma-event-card anim-fade-up" data-id="${event.id}">
        <div class="luma-card-left">
          <div class="luma-card-cover" style="${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? `background: ${getGradientCss(event.coverUrl)};` : ''}">
            ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? '📅' : `<img src="${event.coverUrl}" alt="${event.title}" loading="lazy" />`}
          </div>
        </div>
        <div class="luma-card-right">
          <div class="luma-card-header-row">
            <div class="event-host-badge" data-creator-id="${event.createdBy}">
              <div class="host-avatar-placeholder"><div class="host-avatar-initial">${initial}</div></div>
              <span class="host-name">Cosmos Member</span>
            </div>
            ${priceBadge}
          </div>
          <h4 class="luma-card-title">${event.title}</h4>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            <span class="luma-time-text">${event.time}</span>
          </div>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            <span class="luma-location-text">${event.location}</span>
          </div>
          ${tagsHtml ? `<div class="luma-card-tags-row">${tagsHtml}</div>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

async function fetchHostProfile(uid) {
  if (hostProfileMap.has(uid)) return hostProfileMap.get(uid);
  try {
    const userSnap = await getDoc(doc(db, 'users', uid));
    if (userSnap.exists()) {
      const data = userSnap.data();
      const profile = { name: data.name || 'Cosmos Member', avatarUrl: data.avatarUrl || '' };
      hostProfileMap.set(uid, profile);
      return profile;
    }
  } catch (e) {
    console.warn('[Cosmos Organize] Host fetch error:', uid, e);
  }
  const fallback = { name: 'Cosmos Member', avatarUrl: '' };
  hostProfileMap.set(uid, fallback);
  return fallback;
}

function resolveHostProfiles(outlet) {
  const hostBadges = outlet.querySelectorAll('.event-host-badge');
  hostBadges.forEach(async badge => {
    const creatorId = badge.dataset.creatorId;
    if (!creatorId) return;
    const profile = await fetchHostProfile(creatorId);

    const avatarPlaceholder = badge.querySelector('.host-avatar-placeholder');
    const nameEl = badge.querySelector('.host-name');

    if (avatarPlaceholder && nameEl) {
      if (profile.avatarUrl) {
        avatarPlaceholder.innerHTML = `<img src="${profile.avatarUrl}" class="host-avatar" alt="${profile.name}" />`;
      } else {
        avatarPlaceholder.innerHTML = `<div class="host-avatar-initial">${profile.name.charAt(0)}</div>`;
      }
      nameEl.textContent = profile.name;
    }
  });
}

function attachEventCardListeners(outlet, currentUserId) {
  outlet.querySelectorAll('.luma-event-card').forEach(card => {
    card.addEventListener('click', () => {
      const eventId = card.dataset.id;
      const event = eventsList.find(ev => ev.id === eventId);
      if (event) showEventDetailsModal(outlet, event, currentUserId);
    });
  });
}

async function showEventDetailsModal(outlet, event, currentUserId) {
  let modal = document.getElementById('event-details-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.className = 'modal-overlay hidden';
    modal.id = 'event-details-modal';
    modal.style.zIndex = '99999';
    document.body.appendChild(modal);
  }

  const spotsLeft = event.maxParticipants - event.participantCount;
  const isJoined = registrationsMap.get(event.id) || false;
  const isHost = currentUserId && event.createdBy === currentUserId;

  const defaultName = window.cosmosApp?.userProfile?.name || auth.currentUser?.displayName || '';
  const defaultEmail = auth.currentUser?.email || window.cosmosApp?.userProfile?.email || '';

  // Initial Modal Render
  modal.innerHTML = `
    <div class="modal-card" style="max-width:480px; position:relative; overflow:hidden;">
      <button class="modal-close" id="btn-close-details-modal" aria-label="Close modal" style="position:absolute; right:12px; top:12px; border:none; background:rgba(0,0,0,0.6); border-radius:50%; width:32px; height:32px; display:flex; align-items:center; justify-content:center; color:white; cursor:pointer; z-index:10; transition:all 0.2s;">✕</button>
      
      <div class="event-card-cover" style="height: 160px; margin: -1.5rem -1.5rem 1.25rem -1.5rem; overflow: hidden; ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? `background: ${getGradientCss(event.coverUrl)};` : ''}">
        ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? '<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:3rem;">📅</div>' : `<img src="${event.coverUrl}" style="width: 100%; height: 100%; object-fit: cover;" alt="${event.title}" />`}
      </div>
      
      <div class="event-modal-content" style="max-height: calc(85vh - 180px); overflow-y: auto; padding-right: 4px;">
        <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:0.5rem;">
          <div class="event-host-badge" data-creator-id="${event.createdBy}">
            <div class="host-avatar-placeholder"><div class="host-avatar-initial">${event.title.charAt(0)}</div></div>
            <span class="host-name">Cosmos Member</span>
          </div>
          ${isHost ? '<span class="event-status-pill hosting">👑 Hosted by You</span>' : (isJoined ? '<span class="event-status-pill joined">✓ Participating</span>' : '')}
        </div>

        <h3 style="font-family:var(--font-display); font-size:1.35rem; font-weight:800; color:white; margin:0.25rem 0 0 0;">${event.title}</h3>
        
        <div style="display:flex; flex-direction:column; gap:0.4rem; margin-top:0.25rem;">
          <div class="luma-card-meta">
            <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            <span style="color:var(--text-primary); font-weight:600;">${event.date}</span>
          </div>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            <span class="luma-time-text">${event.time}</span>
          </div>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            <span>${event.location}</span>
          </div>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
            <span>${spotsLeft > 0 ? `${spotsLeft} spots remaining` : 'Sold out'} (${event.participantCount} registered)</span>
          </div>
        </div>
        
        <div style="background:rgba(255,255,255,0.02); border:1px solid rgba(255,255,255,0.05); border-radius:12px; padding:0.85rem; margin-top:0.25rem;">
          <h5 style="margin:0 0 0.4rem 0; font-size:0.8rem; text-transform:uppercase; letter-spacing:0.5px; color:var(--text-muted);">About Event</h5>
          <p style="font-size:0.88rem; color:var(--text-secondary); line-height:1.6; margin:0; white-space:pre-wrap;">${event.description || 'No description provided.'}</p>
        </div>

        ${isHost ? `
          <!-- HOST VIEW: PARTICIPANTS ROSTER -->
          <div class="event-host-admin-panel" id="host-registrants-panel">
            <div class="event-host-admin-header">
              <span class="event-host-admin-title">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                Registered Participants
              </span>
              <span class="event-registrant-badge" id="host-registrant-count">Loading...</span>
            </div>
            <div class="event-registrants-list" id="host-registrants-list">
              <div style="text-align:center; padding:1rem; color:var(--text-muted); font-size:0.82rem;">
                <div class="loading-spinner" style="width:20px;height:20px;margin:0 auto 0.5rem auto;display:block;"></div>
                Loading participant details...
              </div>
            </div>
          </div>
        ` : (isJoined ? `
          <!-- ALREADY REGISTERED VIEW -->
          <div style="background:rgba(16,185,129,0.08); border:1px solid rgba(16,185,129,0.25); border-radius:14px; padding:1rem; text-align:center; margin-top:0.5rem;">
            <div style="font-size:1.5rem; margin-bottom:0.25rem;">🎉</div>
            <h4 style="margin:0; font-size:0.95rem; color:#34d399; font-weight:700;">You're on the Guest List</h4>
            <p style="margin:0.25rem 0 0 0; font-size:0.8rem; color:var(--text-secondary);">We'll notify you when the session begins.</p>
          </div>
        ` : (spotsLeft <= 0 ? `
          <!-- FULL VIEW -->
          <div style="background:rgba(239,68,68,0.08); border:1px solid rgba(239,68,68,0.25); border-radius:14px; padding:1rem; text-align:center; margin-top:0.5rem;">
            <h4 style="margin:0; font-size:0.95rem; color:var(--red); font-weight:700;">Event is Full</h4>
            <p style="margin:0.25rem 0 0 0; font-size:0.8rem; color:var(--text-secondary);">All available participant slots have been claimed.</p>
          </div>
        ` : `
          <!-- PARTICIPATION FORM -->
          <form class="event-participate-form" id="event-participate-form">
            <h4 class="event-participate-title">Participate in this Event</h4>
            <p class="event-participate-subtitle">Confirm your name and email to join the attendee list.</p>
            
            <div class="form-group" style="margin-bottom:0.4rem;">
              <label class="form-label" for="participate-name" style="font-size:0.78rem;">Full Name *</label>
              <input class="form-input" type="text" id="participate-name" value="${defaultName}" required placeholder="Your full name" style="padding:0.65rem 0.85rem; font-size:0.88rem;" />
            </div>

            <div class="form-group" style="margin-bottom:0.75rem;">
              <label class="form-label" for="participate-email" style="font-size:0.78rem;">Email Address *</label>
              <input class="form-input" type="email" id="participate-email" value="${defaultEmail}" required placeholder="name@example.com" style="padding:0.65rem 0.85rem; font-size:0.88rem;" />
            </div>

            <button type="submit" class="btn btn-primary" id="btn-submit-participate" style="width:100%; py:12px; font-weight:700; font-size:0.92rem;">
              Confirm Participation 🚀
            </button>
          </form>
        `))}
      </div>
    </div>
  `;

  resolveHostProfiles(modal);

  // Open modal with smooth transition
  modal.classList.remove('hidden');
  modal.offsetHeight; // force reflow for CSS transition
  modal.classList.add('active');
  document.body.style.overflow = 'hidden';

  const closeModal = () => {
    modal.classList.remove('active');
    setTimeout(() => {
      if (!modal.classList.contains('active')) {
        modal.classList.add('hidden');
      }
    }, 300);
    document.body.style.overflow = '';
  };

  const btnClose = modal.querySelector('#btn-close-details-modal');
  if (btnClose) btnClose.onclick = closeModal;

  // Backdrop click to close
  modal.onclick = (e) => {
    if (e.target === modal) closeModal();
  };

  // If host is viewing, load real-time participants
  if (isHost) {
    try {
      const regSnap = await getDocs(collection(db, 'events', event.id, 'registrants'));
      const countEl = modal.querySelector('#host-registrant-count');
      const listEl = modal.querySelector('#host-registrants-list');

      if (countEl && listEl) {
        const registrants = [];
        regSnap.forEach(d => {
          const data = d.data() || {};
          registrants.push({
            id: d.id,
            name: data.name || 'Anonymous Member',
            email: data.email || 'No email provided',
            registeredAt: data.registeredAt?.toDate ? data.registeredAt.toDate() : null
          });
        });

        countEl.textContent = `${registrants.length} ${registrants.length === 1 ? 'Person' : 'People'}`;

        if (registrants.length === 0) {
          listEl.innerHTML = `
            <div style="text-align:center; padding:1.25rem; color:var(--text-secondary); font-size:0.85rem;">
              <div style="font-size:1.75rem; margin-bottom:0.25rem;">📭</div>
              <strong>No participants yet</strong>
              <div style="font-size:0.78rem; color:var(--text-muted); margin-top:0.2rem;">Share your event to get attendees registered!</div>
            </div>
          `;
        } else {
          listEl.innerHTML = registrants.map(r => {
            const initial = (r.name || 'M').charAt(0).toUpperCase();
            const dateStr = r.registeredAt ? r.registeredAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Recently';
            return `
              <div class="event-registrant-item">
                <div class="event-registrant-avatar">${initial}</div>
                <div class="event-registrant-info">
                  <div class="event-registrant-name">${r.name}</div>
                  <div class="event-registrant-email">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
                    <a href="mailto:${r.email}" style="color:var(--text-secondary); text-decoration:none;" onclick="event.stopPropagation();">${r.email}</a>
                  </div>
                </div>
                <div style="font-size:0.72rem; color:var(--text-muted); text-align:right;">${dateStr}</div>
              </div>
            `;
          }).join('');
        }
      }
    } catch (err) {
      console.error('[Cosmos Organize] Error fetching registrants for host:', err);
    }
  }

  // Handle Participation Form Submission
  const participateForm = modal.querySelector('#event-participate-form');
  if (participateForm) {
    participateForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const nameInput = modal.querySelector('#participate-name');
      const emailInput = modal.querySelector('#participate-email');
      const submitBtn = modal.querySelector('#btn-submit-participate');

      const name = nameInput.value.trim();
      const email = emailInput.value.trim();

      if (!name) {
        showToast('Please enter your full name.', 'error');
        nameInput.focus();
        return;
      }
      if (!email || !email.includes('@')) {
        showToast('Please enter a valid email address.', 'error');
        emailInput.focus();
        return;
      }

      submitBtn.disabled = true;
      submitBtn.textContent = 'Registering...';

      try {
        // 1. Save participant details in registrants subcollection
        await setDoc(doc(db, 'events', event.id, 'registrants', currentUserId), {
          userId: currentUserId,
          name: name,
          email: email,
          registeredAt: serverTimestamp()
        });

        // 2. Increment participant count
        await updateDoc(doc(db, 'events', event.id), {
          participantCount: increment(1)
        });

        // 3. Send notification to participant
        await addDoc(collection(db, 'notifications'), {
          userId: currentUserId,
          type: 'EVENT_REMINDER',
          title: `Registered for ${event.title}`,
          body: "You're all set! We'll remind you when the session starts.",
          timestamp: serverTimestamp(),
          isRead: false,
          actionId: event.id
        });

        // 4. Send notification to event host
        if (event.createdBy && event.createdBy !== currentUserId) {
          await addDoc(collection(db, 'notifications'), {
            userId: event.createdBy,
            type: 'EVENT_REGISTRATION',
            title: `New Participant: ${name}`,
            body: `${name} (${email}) registered for ${event.title}.`,
            timestamp: serverTimestamp(),
            isRead: false,
            actionId: event.id
          });
        }

        // 5. Update local state
        registrationsMap.set(event.id, true);
        event.participantCount += 1;

        showToast(`🎉 You're participating in ${event.title}!`, 'success');
        
        // Re-render modal in registered state & update timeline/your-events
        showEventDetailsModal(outlet, event, currentUserId);
        updateEventsDisplay(outlet, currentUserId);
      } catch (err) {
        console.error('[Cosmos Organize] Registration error:', err);
        showToast('Failed to register: ' + (err.message || 'Unknown error'), 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Confirm Participation 🚀';
      }
    });
  }
}

function cropImage(imgSrc, zoomPercent, offsetYPx, previewHeight) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = imgSrc;
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 800;
      canvas.height = 400;
      const ctx = canvas.getContext('2d');

      const W_orig = img.naturalWidth;
      const H_orig = img.naturalHeight;
      const W_canvas = canvas.width;
      const H_canvas = canvas.height;

      // Base cover calculation
      const R_img = W_orig / H_orig;
      let W_scaled, H_scaled;
      let X_draw, Y_draw;

      // Since canvas is 800x400 (aspect ratio 2.0)
      if (R_img > 2) {
        H_scaled = H_canvas;
        W_scaled = H_canvas * R_img;
        X_draw = (W_canvas - W_scaled) / 2;
        Y_draw = 0;
      } else {
        W_scaled = W_canvas;
        H_scaled = W_canvas / R_img;
        X_draw = 0;
        Y_draw = (H_canvas - H_scaled) / 2;
      }

      const zoom = zoomPercent / 100;
      const scaleFactor = H_canvas / previewHeight;
      const panY_canvas = offsetYPx * scaleFactor;

      ctx.clearRect(0, 0, W_canvas, H_canvas);
      ctx.save();
      
      // Perform CSS-equivalent transformations
      // Translate to canvas center, scale, translate by pan, translate back, and draw
      ctx.translate(W_canvas / 2, H_canvas / 2);
      ctx.scale(zoom, zoom);
      ctx.translate(0, panY_canvas);
      ctx.translate(-W_canvas / 2, -H_canvas / 2);

      ctx.drawImage(img, X_draw, Y_draw, W_scaled, H_scaled);
      ctx.restore();

      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob);
        } else {
          reject(new Error('Canvas toBlob failed'));
        }
      }, 'image/jpeg', 0.85);
    };
    img.onerror = (err) => {
      reject(err);
    };
  });
}

function getGradientCss(coverUrl) {
  const gradient = coverUrl || 'gradient:cosmos-glow';
  switch (gradient) {
    case 'gradient:cosmos-glow':
      return 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #581c87 100%)';
    case 'gradient:sunset-aurora':
      return 'linear-gradient(135deg, #1e1b4b 0%, #701a75 50%, #f43f5e 100%)';
    case 'gradient:cyber-neon':
      return 'linear-gradient(135deg, #020617 0%, #0f766e 60%, #06b6d4 100%)';
    case 'gradient:deep-space':
      return 'linear-gradient(135deg, #030712 0%, #1e1b4b 40%, #db2777 100%)';
    case 'gradient:emerald-matrix':
      return 'linear-gradient(135deg, #022c22 0%, #065f46 50%, #10b981 100%)';
    default:
      return 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #581c87 100%)';
  }
}

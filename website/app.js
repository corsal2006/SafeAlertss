/* ==========================================================================
   SafeAlerts Interactive Website Controller
   ========================================================================== */

document.addEventListener("DOMContentLoaded", () => {
  // 1. Theme Switcher (Dark Default matched to SafeAlerts Android Theme)
  const html = document.documentElement;
  const themeToggle = document.getElementById("themeToggle");
  const themeIcon = document.getElementById("themeIcon");
  const savedTheme = localStorage.getItem("safealerts-theme") || "dark";
  html.setAttribute("data-theme", savedTheme);
  updateThemeIcon(savedTheme);

  themeToggle?.addEventListener("click", () => {
    const next = html.getAttribute("data-theme") === "dark" ? "light" : "dark";
    html.setAttribute("data-theme", next);
    localStorage.setItem("safealerts-theme", next);
    updateThemeIcon(next);
  });

  function updateThemeIcon(theme) {
    if (!themeIcon) return;
    themeIcon.textContent = theme === "dark" ? "☀️" : "🌙";
  }

  // 2. Mobile Navigation Drawer Toggle
  const mobileMenuBtn = document.getElementById("mobileMenuBtn");
  const navLinks = document.getElementById("navLinks");
  mobileMenuBtn?.addEventListener("click", () => {
    navLinks?.classList.toggle("open");
  });
  navLinks?.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => navLinks.classList.remove("open"));
  });

  // 3. Scroll Progress & Active Section Highlight
  const progressBar = document.getElementById("scrollProgress");
  const sections = document.querySelectorAll("section[id]");
  const navAnchors = document.querySelectorAll(".nav-link");

  window.addEventListener(
    "scroll",
    () => {
      const scrollTop = window.scrollY;
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      if (progressBar && docHeight > 0) {
        progressBar.style.width = `${Math.min(100, (scrollTop / docHeight) * 100)}%`;
      }
      let current = "";
      sections.forEach((sec) => {
        if (scrollTop >= sec.offsetTop - 160) {
          current = sec.getAttribute("id") || "";
        }
      });
      navAnchors.forEach((a) => {
        a.classList.toggle("active", a.getAttribute("href") === `#${current}`);
      });
    },
    { passive: true }
  );

  // 4. Cursor-Tracking Specular Highlight on Glass Cards
  document.querySelectorAll(".glass-card").forEach((card) => {
    card.addEventListener("pointermove", (e) => {
      const rect = card.getBoundingClientRect();
      card.style.setProperty("--mx", `${e.clientX - rect.left}px`);
      card.style.setProperty("--my", `${e.clientY - rect.top}px`);
    });
  });

  // 5. Scroll Reveal Observer
  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          revealObserver.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.12 }
  );
  document.querySelectorAll(".reveal").forEach((el) => revealObserver.observe(el));

  // 6. Autoplay Hero Video with Full Volume (graceful browser autoplay fallback)
  const demoVideo = document.getElementById("demoVideo");
  const playToggleBtn = document.getElementById("videoPlayToggle");
  const muteToggleBtn = document.getElementById("videoMuteToggle");

  if (demoVideo) {
    demoVideo.volume = 1.0;
    demoVideo.muted = false;

    const tryAutoplayFullVolume = async () => {
      try {
        demoVideo.volume = 1.0;
        demoVideo.muted = false;
        await demoVideo.play();
        if (muteToggleBtn) muteToggleBtn.textContent = "🔊 Volume 100%";
        if (playToggleBtn) playToggleBtn.textContent = "Pause";
      } catch (_err) {
        // Browser policy blocked unmuted autoplay before first interaction; start muted and unmute on first gesture
        demoVideo.muted = true;
        try {
          await demoVideo.play();
        } catch (_) {}
        if (muteToggleBtn) muteToggleBtn.textContent = "🔇 Tap for Sound (100%)";

        const unlockSound = () => {
          demoVideo.muted = false;
          demoVideo.volume = 1.0;
          if (demoVideo.paused) demoVideo.play().catch(() => {});
          if (muteToggleBtn) muteToggleBtn.textContent = "🔊 Volume 100%";
          ["pointerdown", "keydown", "touchstart"].forEach((evt) =>
            window.removeEventListener(evt, unlockSound)
          );
        };
        ["pointerdown", "keydown", "touchstart"].forEach((evt) =>
          window.addEventListener(evt, unlockSound, { once: true })
        );
      }
    };

    tryAutoplayFullVolume();

    playToggleBtn?.addEventListener("click", () => {
      if (demoVideo.paused) {
        demoVideo.play().catch(() => {});
      } else {
        demoVideo.pause();
      }
    });

    muteToggleBtn?.addEventListener("click", () => {
      demoVideo.muted = !demoVideo.muted;
      if (!demoVideo.muted) demoVideo.volume = 1.0;
      muteToggleBtn.textContent = demoVideo.muted ? "🔇 Muted" : "🔊 Volume 100%";
    });

    demoVideo.addEventListener("play", () => {
      if (playToggleBtn) playToggleBtn.textContent = "Pause";
    });
    demoVideo.addEventListener("pause", () => {
      if (playToggleBtn) playToggleBtn.textContent = "Play";
    });
  }

  // 7. Generic 3-Second Auto-Loop Slide Controller for Virtual Phone Walkthroughs
  function createAutoSlideWalkthrough({
    steps,
    buttonSelector,
    currentImgId,
    nextImgId,
    captionId,
    intervalMs = 3000
  }) {
    const buttons = Array.from(document.querySelectorAll(buttonSelector));
    const currentImg = document.getElementById(currentImgId);
    const nextImg = document.getElementById(nextImgId);
    const captionEl = document.getElementById(captionId);
    if (!buttons.length || !currentImg || !nextImg) return;

    let activeIdx = 0;
    let isAnimating = false;
    let timerId = null;

    function restartProgressBar(idx) {
      buttons.forEach((b, i) => {
        b.classList.toggle("active", i === idx);
        const fill = b.querySelector(".step-progress-fill");
        if (fill) {
          fill.classList.remove("running");
          void fill.offsetWidth; // Force reflow so 3s CSS fill animation restarts cleanly
          if (i === idx) fill.classList.add("running");
        }
      });
    }

    function goToStep(targetIdx) {
      const normalized = ((targetIdx % steps.length) + steps.length) % steps.length;
      if (normalized === activeIdx || isAnimating) return;

      isAnimating = true;
      const stepData = steps[normalized];
      nextImg.src = stepData.img;
      nextImg.classList.remove("slide-in-center");
      currentImg.classList.remove("slide-out-left");
      void nextImg.offsetWidth;

      // Trigger smooth Apple-style horizontal slide transition
      currentImg.classList.add("slide-out-left");
      nextImg.classList.add("slide-in-center");
      if (captionEl) captionEl.textContent = stepData.caption;
      activeIdx = normalized;
      restartProgressBar(activeIdx);

      setTimeout(() => {
        currentImg.src = stepData.img;
        currentImg.style.transition = "none";
        nextImg.style.transition = "none";
        currentImg.classList.remove("slide-out-left");
        nextImg.classList.remove("slide-in-center");
        void currentImg.offsetWidth;
        currentImg.style.transition = "";
        nextImg.style.transition = "";
        isAnimating = false;
      }, 560);
    }

    function startLoop() {
      stopLoop();
      restartProgressBar(activeIdx);
      timerId = setInterval(() => {
        goToStep(activeIdx + 1);
      }, intervalMs);
    }

    function stopLoop() {
      if (timerId) {
        clearInterval(timerId);
        timerId = null;
      }
    }

    buttons.forEach((btn, idx) => {
      btn.addEventListener("click", () => {
        if (idx === activeIdx) {
          startLoop();
          return;
        }
        goToStep(idx);
        startLoop();
      });
    });

    startLoop();
  }

  // Initialize "How to Use SafeAlerts" 3s Looping Slideshow
  createAutoSlideWalkthrough({
    buttonSelector: ".usage-step",
    currentImgId: "usagePhoneImg",
    nextImgId: "usagePhoneImgNext",
    captionId: "usagePhoneCaption",
    intervalMs: 3000,
    steps: [
      {
        img: "assets/app/app_01.jpg",
        caption: "Step 1: Sign in securely with Email/Password, Google Sign-In, or Passwordless Email Link."
      },
      {
        img: "assets/app/app_02.jpg",
        caption: "Step 2: Add trusted contacts and toggle who receives automatic SMS emergency alerts."
      },
      {
        img: "assets/app/app_05.jpg",
        caption: "Step 3: Tap or hold SEND EMERGENCY ALERT on the Home dashboard—or use Quick Actions."
      },
      {
        img: "assets/app/app_07.jpg",
        caption: "Step 4: A 3-second countdown prevents accidental alerts, with instant Dispatch or Cancel."
      },
      {
        img: "assets/app/app_08.jpg",
        caption: "Step 5: Fresh GPS fix locks onto Google Maps while SMS & cloud alerts dispatch simultaneously."
      },
      {
        img: "assets/app/app_12.jpg",
        caption: "Step 6: Recipients receive instant SMS alerts with live location & journey overdue warnings."
      },
      {
        img: "assets/app/app_11.jpg",
        caption: "Step 7: Start a Women's Safety Journey with Google Places search, route ETA & auto-escalation."
      },
      {
        img: "assets/app/app_14.jpg",
        caption: "Step 8: When offline, broadcast signed emergency beacons over Bluetooth Low Energy mesh."
      }
    ]
  });

  // Initialize "Android Install & Setup Guide" 3s Looping Slideshow
  createAutoSlideWalkthrough({
    buttonSelector: ".setup-step",
    currentImgId: "setupPhoneImg",
    nextImgId: "setupPhoneImgNext",
    captionId: "setupPhoneCaption",
    intervalMs: 3000,
    steps: [
      {
        img: "assets/setup/setup_02.jpg",
        caption: "Step 1: Open corsal2006/SafeAlertss on GitHub (or use the Download APK button above) and download the latest APK."
      },
      {
        img: "assets/setup/setup_03.jpg",
        caption: "Step 2: Open Google Play Store → Profile → Play Protect, then tap the top-right Settings gear icon."
      },
      {
        img: "assets/setup/setup_01.jpg",
        caption: "Step 3: Temporarily turn off 'Scan apps with Play Protect' & 'Improve harmful app detection', then install the APK."
      },
      {
        img: "assets/setup/setup_05.jpg",
        caption: "Step 4: Log into SafeAlerts, allow prompts, or open App Info → Permissions (tap ⋮ top-right → Allow restricted settings if disabled)."
      },
      {
        img: "assets/setup/setup_04.jpg",
        caption: "Step 5: Confirm Contacts, Location, Microphone, Nearby devices, Notifications & SMS are all under Allowed."
      }
    ]
  });

  // 8. Virtual Mobile Gallery Category Filtering & Lightbox Modal
  const filterPills = document.querySelectorAll(".filter-pill");
  const galleryCards = document.querySelectorAll(".gallery-card");

  filterPills.forEach((pill) => {
    pill.addEventListener("click", () => {
      const cat = pill.getAttribute("data-filter");
      filterPills.forEach((p) => p.classList.remove("active"));
      pill.classList.add("active");

      galleryCards.forEach((card) => {
        const cardCat = card.getAttribute("data-category");
        const match = cat === "all" || cardCat === cat;
        card.classList.toggle("hidden", !match);
      });
    });
  });

  const lightbox = document.getElementById("lightbox");
  const lightboxImg = document.getElementById("lightboxImg");
  const lightboxTitle = document.getElementById("lightboxTitle");
  const lightboxDesc = document.getElementById("lightboxDesc");
  const lightboxBadge = document.getElementById("lightboxBadge");
  const lightboxClose = document.getElementById("lightboxClose");
  const lightboxPrev = document.getElementById("lightboxPrev");
  const lightboxNext = document.getElementById("lightboxNext");

  let activeCards = [];
  let currentCardIdx = 0;

  function openLightbox(card) {
    activeCards = Array.from(document.querySelectorAll(".gallery-card:not(.hidden)"));
    currentCardIdx = activeCards.indexOf(card);
    renderLightboxCard();
    lightbox?.classList.add("open");
    document.body.style.overflow = "hidden";
  }

  function renderLightboxCard() {
    const card = activeCards[currentCardIdx];
    if (!card) return;
    const img = card.querySelector("img");
    if (lightboxImg && img) lightboxImg.src = img.src;
    if (lightboxTitle) lightboxTitle.textContent = card.getAttribute("data-title") || "";
    if (lightboxDesc) lightboxDesc.textContent = card.getAttribute("data-desc") || "";
    if (lightboxBadge) lightboxBadge.textContent = `${currentCardIdx + 1} / ${activeCards.length}`;
  }

  function closeLightbox() {
    lightbox?.classList.remove("open");
    document.body.style.overflow = "";
  }

  galleryCards.forEach((card) => {
    card.addEventListener("click", () => openLightbox(card));
  });

  lightboxClose?.addEventListener("click", closeLightbox);
  lightbox?.addEventListener("click", (e) => {
    if (e.target === lightbox) closeLightbox();
  });

  lightboxPrev?.addEventListener("click", () => {
    currentCardIdx = (currentCardIdx - 1 + activeCards.length) % activeCards.length;
    renderLightboxCard();
  });
  lightboxNext?.addEventListener("click", () => {
    currentCardIdx = (currentCardIdx + 1) % activeCards.length;
    renderLightboxCard();
  });

  document.addEventListener("keydown", (e) => {
    if (!lightbox?.classList.contains("open")) return;
    if (e.key === "Escape") closeLightbox();
    if (e.key === "ArrowLeft") lightboxPrev?.click();
    if (e.key === "ArrowRight") lightboxNext?.click();
  });

  // 9. Download APK Toast Cue
  const toast = document.getElementById("downloadToast");
  document.querySelectorAll(".apk-download-trigger").forEach((btn) => {
    btn.addEventListener("click", () => {
      if (!toast) return;
      toast.classList.add("show");
      setTimeout(() => toast.classList.remove("show"), 4200);
    });
  });
});

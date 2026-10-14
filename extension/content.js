(() => {
  'use strict';

  // Tab labels to match. Add your X UI language here if it isn't English.
  // If no label matches, the script falls back to tab position
  // (X always shows "For you" first and "Following" second on /home).
  const FOR_YOU_LABELS = ['for you'];
  const FOLLOWING_LABELS = ['following'];

  const HIDDEN_CLASS = 'xfud-hidden';
  const SWITCHING_CLASS = 'xfud-switching';
  const CLICK_COOLDOWN_MS = 500;

  let lastClick = 0;
  let scheduled = false;

  const label = (tab) => (tab.textContent || '').trim().toLowerCase();

  function findHomeTabs() {
    const tablist = document.querySelector('[data-testid="primaryColumn"] [role="tablist"]');
    if (!tablist) return null;

    const tabs = [...tablist.querySelectorAll('[role="tab"]')];
    if (tabs.length < 2) return null;

    let forYou = tabs.find((t) => FOR_YOU_LABELS.includes(label(t)));
    let following = tabs.find((t) => FOLLOWING_LABELS.includes(label(t)));
    if (!forYou || !following) {
      [forYou, following] = tabs;
    }
    return { forYou, following };
  }

  function process() {
    scheduled = false;
    const root = document.documentElement;

    if (location.pathname !== '/home') {
      root.classList.remove(SWITCHING_CLASS);
      return;
    }

    const tabs = findHomeTabs();
    if (!tabs) return;

    const wrapper = tabs.forYou.closest('[role="presentation"]') || tabs.forYou;
    wrapper.classList.add(HIDDEN_CLASS);

    const forYouSelected = tabs.forYou.getAttribute('aria-selected') === 'true';
    root.classList.toggle(SWITCHING_CLASS, forYouSelected);

    if (forYouSelected && Date.now() - lastClick > CLICK_COOLDOWN_MS) {
      lastClick = Date.now();
      tabs.following.click();
    }
  }

  function schedule() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(process);
  }

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['aria-selected'],
  });
  schedule();
})();

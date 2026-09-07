(() => {
  const releaseUrl = 'https://github.com/anshi/fairshare/releases/latest/download/fairshare.apk';
  document.querySelectorAll('[data-download]').forEach((link) => { link.href = releaseUrl; });
  const token = new URLSearchParams(window.location.search).get('token');
  const tokenElement = document.querySelector('[data-invite-token]');
  if (tokenElement) {
    tokenElement.textContent = token || 'No invite token was included in this link.';
    const copy = document.querySelector('[data-copy-token]');
    copy?.addEventListener('click', async () => {
      if (!token) return;
      await navigator.clipboard.writeText(token);
      copy.textContent = 'Copied';
    });
  }
})();


async function(request) {
  const url = new URL(request.url, window.location.href);
  const apiIndex = url.pathname.indexOf('/api/');
  // Swagger may omit method when fetching its configuration or API definition.
  const method = (request.method || 'GET').toUpperCase();
  const changesData = !['GET', 'HEAD', 'OPTIONS'].includes(method);
  // Never send this application's CSRF token to a different origin.
  if (url.origin === window.location.origin && apiIndex >= 0 && changesData) {
    const csrfUrl = url.origin + url.pathname.slice(0, apiIndex) + '/api/auth/csrf';
    const response = await fetch(csrfUrl, { credentials: 'same-origin' });
    if (!response.ok) throw new Error('CSRF token request failed');
    const csrf = await response.json();
    request.headers = request.headers || {};
    request.headers[csrf.headerName] = csrf.token;
  }
  return request;
}

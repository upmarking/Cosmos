/* ============================================================
   Cosmos PWA — Hash-Based SPA Router
   ============================================================ */

class CosmosRouter {
  constructor() {
    this.routes = {};
    this.currentRoute = null;
    this.beforeEach = null;
    this.outlet = null;
    this.routeCleanup = null;
    this.subPageRoutes = new Set(['/edit-profile', '/help-support']);
    window.addEventListener('hashchange', () => this.handleRoute());
    
    // Handle cases where the load event has already fired
    if (document.readyState === 'complete') {
      setTimeout(() => this.handleRoute(), 0);
    } else {
      window.addEventListener('load', () => this.handleRoute());
    }
  }

  setOutlet(selector) {
    this.outlet = document.querySelector(selector);
  }

  addRoute(path, handler) {
    this.routes[path] = handler;
  }

  setGuard(fn) {
    this.beforeEach = fn;
  }

  navigate(path) {
    window.location.hash = path;
  }

  getBackRoute(defaultRoute = '/settings') {
    const params = this.getParams();
    if (params[0] === 'edit-profile' && params[1] === 'settings') {
      return '/settings';
    }
    return defaultRoute;
  }

  getHash() {
    return window.location.hash.slice(1) || '/connect';
  }

  getParams() {
    const hash = this.getHash();
    const parts = hash.split('/').filter(Boolean);
    return parts;
  }

  async handleRoute() {
    const path = this.getHash();
    const basePath = '/' + (path.split('/').filter(Boolean)[0] || 'connect');

    // Run guard
    if (this.beforeEach) {
      const allowed = await this.beforeEach(basePath);
      if (!allowed) return;
    }

    // Find matching route
    const handler = this.routes[basePath];
    if (handler && this.outlet) {
      if (this.routeCleanup) {
        this.routeCleanup();
        this.routeCleanup = null;
      }

      document.body.classList.toggle('sub-page-mode', this.subPageRoutes.has(basePath));

      // Update active nav
      document.querySelectorAll('.nav-tab').forEach((tab) => {
        tab.classList.toggle('active', tab.dataset.route === basePath);
      });

      // Page transition
      this.outlet.classList.add('page-exit');
      await new Promise((r) => setTimeout(r, 150));
      
      this.currentRoute = basePath;
      const cleanup = await handler(this.outlet, path);
      if (typeof cleanup === 'function') {
        this.routeCleanup = cleanup;
      }
      
      this.outlet.classList.remove('page-exit');
      this.outlet.classList.add('page-enter');
      await new Promise((r) => setTimeout(r, 300));
      this.outlet.classList.remove('page-enter');
    } else if (!handler) {
      // Default to connect
      this.navigate('/connect');
    }
  }
}

const router = new CosmosRouter();
export default router;

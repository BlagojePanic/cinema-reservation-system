import { FormEvent, useEffect, useState } from 'react';
import {
  apiRequest,
  AuthResponse,
  clearToken,
  getToken,
  saveToken,
  UserResponse,
} from './api';

type HealthState = 'checking' | 'up' | 'down';
type Page = 'home' | 'register' | 'login';

function App() {
  const [health, setHealth] = useState<HealthState>('checking');
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState('');

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Backend health check failed');
        }
        return response.json() as Promise<{ status: string }>;
      })
      .then((data) => setHealth(data.status === 'UP' ? 'up' : 'down'))
      .catch(() => setHealth('down'));
  }, []);

  useEffect(() => {
    const onPopState = () => setPage(getPageFromPath());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const message =
    health === 'checking'
      ? 'Checking backend status...'
      : health === 'up'
        ? 'Backend is available.'
        : 'Backend is not available.';

  function navigate(nextPage: Page, clearStatus = true) {
    const path = nextPage === 'home' ? '/' : `/${nextPage}`;
    window.history.pushState({}, '', path);
    setPage(nextPage);
    if (clearStatus) {
      setStatusMessage('');
    }
  }

  async function testEndpoint(path: '/api/user/test' | '/api/admin/test') {
    setStatusMessage('Sending authenticated request...');
    try {
      const response = await apiRequest<{ message: string }>(path);
      setStatusMessage(response.message);
    } catch (error) {
      setStatusMessage(getErrorMessage(error));
    }
  }

  function logout() {
    clearToken();
    setCurrentUser(null);
    setStatusMessage('Token removed from localStorage.');
  }

  return (
    <main className="app-shell">
      <section className="status-panel">
        <p className="eyebrow">CryptoCinema</p>
        <nav className="nav-row" aria-label="Primary navigation">
          <button type="button" onClick={() => navigate('home')}>
            Home
          </button>
          <button type="button" onClick={() => navigate('register')}>
            Register
          </button>
          <button type="button" onClick={() => navigate('login')}>
            Login
          </button>
        </nav>

        {page === 'home' && (
          <>
            <h1>Authentication test</h1>
            <p>{message}</p>
            <div className="actions">
              <button type="button" onClick={() => testEndpoint('/api/user/test')}>
                Test USER endpoint
              </button>
              <button type="button" onClick={() => testEndpoint('/api/admin/test')}>
                Test ADMIN endpoint
              </button>
              <button type="button" onClick={logout}>
                Logout
              </button>
            </div>
            {currentUser && (
              <p className="session">
                Logged in as {currentUser.email} ({currentUser.role})
              </p>
            )}
            {getToken() && !currentUser && <p className="session">JWT is saved in localStorage.</p>}
          </>
        )}

        {page === 'register' && (
          <RegisterForm
            onRegistered={(user) => {
              setCurrentUser(user);
              setStatusMessage(`Registered ${user.email} with role ${user.role}.`);
            }}
          />
        )}

        {page === 'login' && (
          <LoginForm
            onLoggedIn={(auth) => {
              saveToken(auth.token);
              setCurrentUser(auth.user);
              setStatusMessage(`Logged in as ${auth.user.email}.`);
              navigate('home', false);
            }}
          />
        )}

        {statusMessage && <p className="status-message">{statusMessage}</p>}
      </section>
    </main>
  );
}

function RegisterForm({ onRegistered }: { onRegistered: (user: UserResponse) => void }) {
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    const formElement = event.currentTarget;
    const form = new FormData(formElement);

    try {
      const user = await apiRequest<UserResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          firstName: form.get('firstName'),
          lastName: form.get('lastName'),
          email: form.get('email'),
          password: form.get('password'),
        }),
      });
      formElement.reset();
      onRegistered(user);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <>
      <h1>Register</h1>
      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          First name
          <input name="firstName" required />
        </label>
        <label>
          Last name
          <input name="lastName" required />
        </label>
        <label>
          Email
          <input name="email" type="email" required />
        </label>
        <label>
          Password
          <input name="password" type="password" minLength={8} required />
        </label>
        <button type="submit">Create account</button>
      </form>
      {error && <p className="error-message">{error}</p>}
    </>
  );
}

function LoginForm({ onLoggedIn }: { onLoggedIn: (auth: AuthResponse) => void }) {
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    const formElement = event.currentTarget;
    const form = new FormData(formElement);

    try {
      const auth = await apiRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: form.get('email'),
          password: form.get('password'),
        }),
      });
      formElement.reset();
      onLoggedIn(auth);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <>
      <h1>Login</h1>
      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          Email
          <input name="email" type="email" required />
        </label>
        <label>
          Password
          <input name="password" type="password" required />
        </label>
        <button type="submit">Login</button>
      </form>
      {error && <p className="error-message">{error}</p>}
    </>
  );
}

function getPageFromPath(): Page {
  if (window.location.pathname === '/register') {
    return 'register';
  }
  if (window.location.pathname === '/login') {
    return 'login';
  }
  return 'home';
}

function getErrorMessage(error: unknown) {
  if (typeof error === 'object' && error && 'message' in error) {
    return String(error.message);
  }
  return 'Unexpected error';
}

export default App;

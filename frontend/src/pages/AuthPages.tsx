import { FormEvent, useState } from 'react';
import { apiRequest, AuthResponse, saveToken, UserResponse } from '../api';
import { getErrorMessage } from '../utils/format';

export function LoginPage({
  onLoggedIn,
}: {
  onLoggedIn: (auth: AuthResponse) => void;
}) {
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError('');
    try {
      const auth = await apiRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: form.get('email'),
          password: form.get('password'),
        }),
      });
      saveToken(auth.token);
      formElement.reset();
      onLoggedIn(auth);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <p className="eyebrow">Welcome back</p>
        <h1>Login</h1>
        <label>Email<input name="email" type="email" required /></label>
        <label>Password<input name="password" type="password" required /></label>
        <button type="submit">Login</button>
        {error && <p className="error-message">{error}</p>}
      </form>
    </main>
  );
}

export function RegisterPage({
  onRegistered,
}: {
  onRegistered: (user: UserResponse) => void;
}) {
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError('');
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
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <p className="eyebrow">Join CRYPTOCINEMA</p>
        <h1>Create account</h1>
        <label>First name<input name="firstName" required /></label>
        <label>Last name<input name="lastName" required /></label>
        <label>Email<input name="email" type="email" required /></label>
        <label>Password<input name="password" type="password" minLength={8} required /></label>
        <button type="submit">Create account</button>
        {error && <p className="error-message">{error}</p>}
      </form>
    </main>
  );
}

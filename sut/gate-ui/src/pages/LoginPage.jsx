import { useState } from 'react';
import { api } from '../api/client.js';
import Alert from '../components/Alert.jsx';

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      onLogin(await api.login(username, password));
    } catch (err) {
      setError(err.status === 401 ? 'Invalid username or password' : err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card narrow" aria-labelledby="login-title">
      <h1 id="login-title">Sign in</h1>
      <p className="muted">Drivers book gate slots. Gate clerks check trucks in.</p>
      <form onSubmit={submit} noValidate data-testid="login-form">
        <label className="field">
          <span>Username</span>
          <input
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            data-testid="login-username"
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            data-testid="login-password"
            required
          />
        </label>
        <Alert kind="error" testId="login-error">
          {error}
        </Alert>
        <button type="submit" className="btn btn-primary" disabled={busy || !username || !password} data-testid="login-submit">
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </section>
  );
}

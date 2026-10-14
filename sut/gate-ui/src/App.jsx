import { useEffect, useState } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, session } from './api/client.js';
import LoginPage from './pages/LoginPage.jsx';
import DriverPage from './pages/DriverPage.jsx';
import ClerkPage from './pages/ClerkPage.jsx';
import Header from './components/Header.jsx';

const home = (user) => (user.role === 'CLERK' ? '/clerk' : '/driver');

export default function App() {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(Boolean(session.token));
  const navigate = useNavigate();

  useEffect(() => {
    if (!session.token) return;
    api
      .me()
      .then(setUser)
      .catch(() => session.clear())
      .finally(() => setChecking(false));
  }, []);

  const onLogin = (result) => {
    session.set(result.token);
    setUser(result.user);
    navigate(home(result.user), { replace: true });
  };

  const onLogout = () => {
    session.clear();
    setUser(null);
    navigate('/login', { replace: true });
  };

  if (checking) {
    return (
      <p className="loading" data-testid="app-loading" role="status">
        Loading…
      </p>
    );
  }

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <Header user={user} onLogout={onLogout} />
      <main id="main" className="container">
        <Routes>
          <Route path="/login" element={user ? <Navigate to={home(user)} replace /> : <LoginPage onLogin={onLogin} />} />
          <Route
            path="/driver"
            element={user?.role === 'DRIVER' ? <DriverPage /> : <Navigate to={user ? home(user) : '/login'} replace />}
          />
          <Route
            path="/clerk"
            element={user?.role === 'CLERK' ? <ClerkPage /> : <Navigate to={user ? home(user) : '/login'} replace />}
          />
          <Route path="*" element={<Navigate to={user ? home(user) : '/login'} replace />} />
        </Routes>
      </main>
    </>
  );
}

export default function Header({ user, onLogout }) {
  return (
    <header className="topbar">
      <div className="container topbar-inner">
        <div className="brand" data-testid="brand">
          <svg aria-hidden="true" width="28" height="28" viewBox="0 0 32 32">
            <rect width="32" height="32" rx="6" fill="#f2a900" />
            <path d="M10 9v14M14 9v14M18 9v14M22 9v14" stroke="#0b3d5c" strokeWidth="1.6" />
          </svg>
          <span>Gate Appointments</span>
        </div>
        {user && (
          <div className="user">
            <span data-testid="current-user">
              {user.displayName} <span className="role-tag">{user.role === 'CLERK' ? 'Gate clerk' : 'Driver'}</span>
            </span>
            <button type="button" className="btn btn-ghost" data-testid="logout-button" onClick={onLogout}>
              Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}

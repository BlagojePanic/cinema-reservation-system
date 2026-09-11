import { ReactNode, useEffect, useRef, useState } from 'react';
import { UserResponse } from '../api';

type LayoutProps = {
  children: ReactNode;
  currentUser: UserResponse | null;
  onNavigate: (path: string) => void;
  onLogout: () => void;
};

export function Layout({ children, currentUser, onNavigate, onLogout }: LayoutProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!menuOpen) return;

    function closeOnOutsideClick(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [menuOpen]);

  function navigateFromMenu(path: string) {
    setMenuOpen(false);
    onNavigate(path);
  }

  function logoutFromMenu() {
    setMenuOpen(false);
    onLogout();
  }

  return (
    <div className="site-shell">
      <header className="topbar">
        <button className="brand" type="button" onClick={() => onNavigate('/')}>
          CRYPTOCINEMA
        </button>
        <nav className="main-nav">
          <button type="button" onClick={() => onNavigate('/')}>Home</button>
          <button type="button" onClick={() => onNavigate('/movies')}>Movies</button>
          {currentUser?.role === 'ADMIN' && (
            <button type="button" onClick={() => onNavigate('/admin')}>Admin</button>
          )}
        </nav>
        <div className="account-nav">
          {!currentUser ? (
            <>
              <button type="button" onClick={() => onNavigate('/login')}>Login</button>
              <button className="accent-button" type="button" onClick={() => onNavigate('/register')}>Register</button>
            </>
          ) : (
            <div className="user-menu" ref={menuRef}>
              <button className="user-menu-trigger" type="button" onClick={() => setMenuOpen((open) => !open)}>
                <span className="user-icon" aria-hidden="true">U</span>
                <span>{currentUser.firstName || currentUser.email}</span>
                <span className="chevron" aria-hidden="true">v</span>
              </button>
              {menuOpen && (
                <div className="user-dropdown">
                  <button type="button" onClick={() => navigateFromMenu('/account')}>My Account</button>
                  <button type="button" onClick={() => navigateFromMenu('/reservations')}>My Reservations</button>
                  <button type="button" onClick={logoutFromMenu}>Logout</button>
                </div>
              )}
            </div>
          )}
        </div>
      </header>
      {children}
      <footer className="footer">CRYPTOCINEMA | Digital reservations, Sepolia payments and QR tickets</footer>
    </div>
  );
}

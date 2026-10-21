import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext.jsx';

export default function Navbar() {
  const { user, logout, unread } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  function handleLogout() {
    logout();
    setMenuOpen(false);
    navigate('/');
  }

  const close = () => setMenuOpen(false);

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand" onClick={close}>
          <span className="brand-mark" aria-hidden="true">★</span>
          CampusRate
        </Link>
        <button
          type="button"
          className="menu-button"
          aria-expanded={menuOpen}
          aria-label="Menu"
          onClick={() => setMenuOpen((open) => !open)}
        >
          ☰
        </button>
        <nav className={`nav-links ${menuOpen ? 'open' : ''}`} onClick={close}>
          <NavLink to="/rankings">Rankings</NavLink>
          <NavLink to="/compare">Compare</NavLink>
          <NavLink to="/fit">Find my fit</NavLink>
          <NavLink to="/worth-it">Worth it?</NavLink>
          {user ? (
            <>
              <NavLink to="/me/notifications" className="bell" aria-label={`Notifications, ${unread} unread`}>
                🔔{unread > 0 && <span className="bell-count">{unread}</span>}
              </NavLink>
              <NavLink to="/me/reviews">My reviews</NavLink>
              <NavLink to="/me/profile">Profile</NavLink>
              {user.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
              <button type="button" className="link-button" onClick={handleLogout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Log in</NavLink>
              <Link to="/register" className="button button-small">
                Sign up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}

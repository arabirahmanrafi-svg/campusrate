import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import * as api from './api.js';

/**
 * Keeps track of who is logged in (and how many unread notifications they have), for the whole app.
 * Any component can call useAuth() to get { user, login, register, logout, unread, ... }.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(Boolean(api.getToken()));
  const [unread, setUnread] = useState(0);

  // On page load, check whether a saved token is still valid.
  useEffect(() => {
    if (!api.getToken()) return;
    api
      .getMe()
      .then(setUser)
      .catch(() => api.saveToken(null)) // expired or invalid: forget it
      .finally(() => setChecking(false));
  }, []);

  const refreshUnread = useCallback(() => {
    if (!api.getToken()) {
      setUnread(0);
      return;
    }
    api
      .getNotifications()
      .then((data) => setUnread(data.unread))
      .catch(() => setUnread(0));
  }, []);

  // Check for new notifications when someone logs in, then every 2 minutes.
  useEffect(() => {
    if (!user) {
      setUnread(0);
      return undefined;
    }
    refreshUnread();
    const timer = setInterval(refreshUnread, 120_000);
    return () => clearInterval(timer);
  }, [user, refreshUnread]);

  async function handleAuth(request) {
    const { token, user: loggedIn } = await request;
    api.saveToken(token);
    setUser(loggedIn);
    return loggedIn;
  }

  const value = {
    user,
    setUser,
    checking,
    unread,
    refreshUnread,
    login: (form) => handleAuth(api.login(form)),
    register: (form) => handleAuth(api.register(form)),
    logout: () => {
      api.saveToken(null);
      setUser(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

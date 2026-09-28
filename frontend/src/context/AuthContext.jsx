import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import * as authApi from '../api/auth';

const AuthContext = createContext(null);

/** Không đọc lại /auth/me dày hơn mức này — chuyển trang và focus tab thường bắn cùng lúc. */
const REVALIDATE_MIN_INTERVAL_MS = 2000;

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // Chưa biết đã đăng nhập hay chưa cho tới khi /auth/me trả lời — tránh chớp màn
  // hình đăng nhập với người đang có phiên hợp lệ.
  const [loading, setLoading] = useState(true);
  const location = useLocation();

  const lastCheckRef = useRef(0);
  // Tăng mỗi lần đăng nhập / đăng xuất: câu trả lời /auth/me gửi từ phiên cũ về muộn thì bỏ qua,
  // không để nó "đăng nhập lại" người vừa bấm đăng xuất.
  const epochRef = useRef(0);

  /** Trả về user vừa đọc được để nơi gọi dùng ngay, không phải chờ state cập nhật. */
  const refresh = useCallback(async () => {
    lastCheckRef.current = Date.now();
    try {
      const me = await authApi.fetchCurrentUser();
      setUser(me);
      return me;
    } catch {
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Đọc lại quyền từ backend trong lúc đang dùng app — Manager tick / bỏ tick quyền nghiệp vụ,
   * khóa tài khoản… thì sidebar và các nút đổi theo ở lần chuyển trang tiếp theo, không phải đăng
   * nhập lại. Khác {@link refresh}: không chớp màn hình, lỗi mạng thì giữ nguyên người dùng, chỉ
   * 401 (phiên đã hết / bị khóa) mới đưa về trang đăng nhập.
   */
  const revalidate = useCallback(async () => {
    const now = Date.now();
    if (now - lastCheckRef.current < REVALIDATE_MIN_INTERVAL_MS) return;
    lastCheckRef.current = now;
    const epoch = epochRef.current;
    try {
      const me = await authApi.fetchCurrentUser();
      if (epoch !== epochRef.current) return;
      // Giữ nguyên object cũ nếu không có gì đổi để các màn hình không vẽ lại vô ích.
      setUser((prev) => (JSON.stringify(prev) === JSON.stringify(me) ? prev : me));
    } catch (err) {
      if (epoch === epochRef.current && err?.response?.status === 401) {
        setUser(null);
      }
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Mỗi lần chuyển trang: đọc lại quyền (chỉ khi đang đăng nhập).
  const signedIn = user != null;
  useEffect(() => {
    if (signedIn) revalidate();
  }, [location.pathname, signedIn, revalidate]);

  // Quay lại tab / cửa sổ sau một lúc: quyền có thể đã đổi trong lúc vắng mặt.
  useEffect(() => {
    if (!signedIn) return undefined;
    const onFocus = () => revalidate();
    const onVisible = () => {
      if (document.visibilityState === 'visible') revalidate();
    };
    window.addEventListener('focus', onFocus);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [signedIn, revalidate]);

  const signIn = useCallback(async (email, password) => {
    epochRef.current += 1;
    await authApi.login(email, password);
    const me = await authApi.fetchCurrentUser();
    lastCheckRef.current = Date.now();
    setUser(me);
    return me;
  }, []);

  const signOut = useCallback(async () => {
    epochRef.current += 1;
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, loading, signIn, signOut, refresh }),
    [user, loading, signIn, signOut, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth phải được dùng bên trong <AuthProvider>');
  }
  return context;
}

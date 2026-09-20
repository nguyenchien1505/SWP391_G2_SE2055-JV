import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import LocationsPage from './pages/LocationsPage';
import AppLayout from './components/AppLayout';

function RequireAuth({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  return user ? children : <Navigate to="/dang-nhap" replace />;
}

export default function App() {
  const { user, loading } = useAuth();

  return (
    <Routes>
      <Route
        path="/dang-nhap"
        element={
          loading ? (
            <div className="boot-screen">Đang tải…</div>
          ) : user ? (
            <Navigate to="/khach-san" replace />
          ) : (
            <LoginPage />
          )
        }
      />
      <Route
        path="/khach-san"
        element={
          <RequireAuth>
            <AppLayout>
              <LocationsPage />
            </AppLayout>
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/khach-san" replace />} />
    </Routes>
  );
}

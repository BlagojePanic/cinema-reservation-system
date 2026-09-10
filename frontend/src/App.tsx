import { ReactNode, useEffect, useMemo, useState } from 'react';
import { apiRequest, clearToken, getToken, UserResponse } from './api';
import { Layout } from './components/Layout';
import {
  AdminDashboardPage,
  AdminMoviesPage,
  AdminReservationsPage,
  AdminScreeningsPage,
  AdminStructurePage,
  AdminTicketValidationPage,
} from './pages/AdminPages';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { HomePage } from './pages/HomePage';
import { MovieDetailsPage, MoviesPage, RepertoirePage, SeatSelectionPage } from './pages/MoviePages';
import { AccountPage, ReservationsPage, TicketsPage } from './pages/UserPages';

type Route =
  | { name: 'home' }
  | { name: 'login' }
  | { name: 'register' }
  | { name: 'movies' }
  | { name: 'movieDetails'; id: number }
  | { name: 'repertoire' }
  | { name: 'seats'; id: number }
  | { name: 'account' }
  | { name: 'reservations' }
  | { name: 'tickets' }
  | { name: 'admin' }
  | { name: 'adminMovies' }
  | { name: 'adminStructure' }
  | { name: 'adminScreenings' }
  | { name: 'adminReservations' }
  | { name: 'adminTickets' }
  | { name: 'notFound' };

function App() {
  const [path, setPath] = useState(() => window.location.pathname);
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const route = useMemo(() => parseRoute(path), [path]);

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      setAuthChecked(true);
      return;
    }

    apiRequest<UserResponse>('/api/auth/me')
      .then((user) => setCurrentUser(user))
      .catch(() => {
        clearToken();
        setCurrentUser(null);
      })
      .finally(() => setAuthChecked(true));
  }, []);

  function navigate(nextPath: string) {
    window.history.pushState({}, '', nextPath);
    setPath(nextPath);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function logout() {
    clearToken();
    setCurrentUser(null);
    navigate('/');
  }

  function requireUser(content: ReactNode) {
    if (!authChecked) {
      return <main className="page"><p>Checking session...</p></main>;
    }
    if (!currentUser) {
      return (
        <main className="page">
          <h1>Login required</h1>
          <button type="button" onClick={() => navigate('/login')}>Login</button>
        </main>
      );
    }
    return content;
  }

  function requireAdmin(content: ReactNode) {
    if (!authChecked) {
      return <main className="page"><p>Checking session...</p></main>;
    }
    if (currentUser?.role !== 'ADMIN') {
      return <main className="page"><h1>Forbidden</h1><p>Admin role is required.</p></main>;
    }
    return content;
  }

  const page = (() => {
    switch (route.name) {
      case 'home':
        return <HomePage onNavigate={navigate} />;
      case 'login':
        return <LoginPage onLoggedIn={(auth) => { setCurrentUser(auth.user); navigate('/'); }} />;
      case 'register':
        return <RegisterPage onRegistered={() => navigate('/login')} />;
      case 'movies':
        return <MoviesPage onNavigate={navigate} />;
      case 'movieDetails':
        return <MovieDetailsPage movieId={route.id} onNavigate={navigate} />;
      case 'repertoire':
        return <RepertoirePage onNavigate={navigate} />;
      case 'seats':
        return <SeatSelectionPage screeningId={route.id} currentUser={currentUser} onNavigate={navigate} />;
      case 'account':
        return requireUser(<AccountPage currentUser={currentUser} onNavigate={navigate} onLogout={logout} />);
      case 'reservations':
        return requireUser(<ReservationsPage onNavigate={navigate} onLogout={logout} />);
      case 'tickets':
        return requireUser(<TicketsPage onNavigate={navigate} onLogout={logout} />);
      case 'admin':
        return requireAdmin(<AdminDashboardPage onNavigate={navigate} />);
      case 'adminMovies':
        return requireAdmin(<AdminMoviesPage onNavigate={navigate} />);
      case 'adminStructure':
        return requireAdmin(<AdminStructurePage onNavigate={navigate} />);
      case 'adminScreenings':
        return requireAdmin(<AdminScreeningsPage onNavigate={navigate} />);
      case 'adminReservations':
        return requireAdmin(<AdminReservationsPage onNavigate={navigate} />);
      case 'adminTickets':
        return requireAdmin(<AdminTicketValidationPage onNavigate={navigate} />);
      default:
        return (
          <main className="page">
            <h1>Page not found</h1>
            <button type="button" onClick={() => navigate('/')}>Home</button>
          </main>
        );
    }
  })();

  return (
    <Layout currentUser={currentUser} onNavigate={navigate} onLogout={logout}>
      {page}
    </Layout>
  );
}

function parseRoute(path: string): Route {
  if (path === '/') return { name: 'home' };
  if (path === '/login') return { name: 'login' };
  if (path === '/register') return { name: 'register' };
  if (path === '/movies') return { name: 'movies' };
  if (path === '/repertoire') return { name: 'repertoire' };
  if (path === '/account') return { name: 'account' };
  if (path === '/reservations') return { name: 'reservations' };
  if (path === '/tickets') return { name: 'tickets' };
  if (path === '/admin') return { name: 'admin' };
  if (path === '/admin/movies') return { name: 'adminMovies' };
  if (path === '/admin/structure') return { name: 'adminStructure' };
  if (path === '/admin/screenings') return { name: 'adminScreenings' };
  if (path === '/admin/reservations') return { name: 'adminReservations' };
  if (path === '/admin/tickets') return { name: 'adminTickets' };

  const movieMatch = path.match(/^\/movies\/(\d+)$/);
  if (movieMatch) return { name: 'movieDetails', id: Number(movieMatch[1]) };

  const seatsMatch = path.match(/^\/screenings\/(\d+)\/seats$/);
  if (seatsMatch) return { name: 'seats', id: Number(seatsMatch[1]) };

  return { name: 'notFound' };
}

export default App;

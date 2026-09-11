import { useEffect, useState } from 'react';
import {
  apiRequest,
  CinemaResponse,
  CityResponse,
  MovieResponse,
  ScreeningResponse,
  UserResponse,
} from '../api';
import { MovieCard, Poster } from '../components/MovieCard';
import { SeatMap } from '../components/SeatMap';
import { formatDate, formatTime, getErrorMessage } from '../utils/format';

export function MoviesPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<MovieResponse[]>('/api/movies')
      .then((response) => {
        setMovies(response);
        setError('');
      })
      .catch(() => setError('Unable to load movies. Please try again.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="page">
      <h1>Movies</h1>
      {loading && <p className="page-message">Loading movies...</p>}
      {error && <p className="error-message">{error}</p>}
      {!loading && !error && movies.length === 0 && <p className="empty-state">No movies are currently available.</p>}
      {!loading && !error && movies.length > 0 && (
        <div className="movie-grid">
          {movies.map((movie) => <MovieCard key={movie.id} movie={movie} onOpen={(id) => onNavigate(`/movies/${id}`)} />)}
        </div>
      )}
    </main>
  );
}

export function MovieDetailsPage({
  movieId,
  initialSearch,
  onNavigate,
}: {
  movieId: number;
  initialSearch?: string;
  onNavigate: (path: string) => void;
}) {
  const [movie, setMovie] = useState<MovieResponse | null>(null);
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [cityId, setCityId] = useState(() => new URLSearchParams(initialSearch).get('cityId') ?? '');
  const [cinemaId, setCinemaId] = useState(() => new URLSearchParams(initialSearch).get('cinemaId') ?? '');
  const [date, setDate] = useState(() => new URLSearchParams(initialSearch).get('date') ?? '');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      apiRequest<MovieResponse>(`/api/movies/${movieId}`),
      apiRequest<CityResponse[]>('/api/cities'),
      apiRequest<CinemaResponse[]>('/api/cinemas'),
    ])
      .then(([nextMovie, nextCities, nextCinemas]) => {
        setMovie(nextMovie);
        setCities(nextCities);
        setCinemas(nextCinemas);
      })
      .catch(() => setError('Unable to load movie details. Please try again.'))
      .finally(() => setLoading(false));
  }, [movieId]);

  useEffect(() => {
    const params = new URLSearchParams();
    params.set('movieId', String(movieId));
    if (cityId) params.set('cityId', cityId);
    if (cinemaId) params.set('cinemaId', cinemaId);
    if (date) params.set('date', date);
    apiRequest<ScreeningResponse[]>(`/api/screenings?${params.toString()}`)
      .then((response) => {
        setScreenings(response);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [movieId, cityId, cinemaId, date]);

  if (!movie) {
    return <main className="page">{loading && <p>Loading movie...</p>}{error && <p className="error-message">{error}</p>}</main>;
  }

  const availableCinemas = cityId ? cinemas.filter((cinema) => cinema.city.id === Number(cityId)) : cinemas;

  return (
    <main className="page">
      <section className="movie-detail-page">
        <div className="detail-poster"><Poster movie={movie} /></div>
        <div className="detail-copy">
          <p className="eyebrow">{movie.genre}</p>
          <h1>{movie.title}</h1>
          <div className="meta-row">
            <span>{movie.durationMinutes} min</span>
            {movie.ageRating && <span>{movie.ageRating}</span>}
            {movie.releaseDate && <span>{formatDate(movie.releaseDate)}</span>}
          </div>
          <p>{movie.description}</p>
          {movie.director && <span>Director: {movie.director}</span>}
          {movie.trailerUrl && (
            <video className="trailer-player" src={movie.trailerUrl} controls />
          )}
        </div>
      </section>

      <section className="content-section">
        <h2>Available Screenings</h2>
        <div className="filters-grid">
          <label>City
            <select value={cityId} onChange={(event) => setCityId(event.target.value)}>
              <option value="">All cities</option>
              {cities.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}
            </select>
          </label>
          <label>Cinema
            <select value={cinemaId} onChange={(event) => setCinemaId(event.target.value)}>
              <option value="">All cinemas</option>
              {availableCinemas.map((cinema) => <option key={cinema.id} value={cinema.id}>{cinema.name}</option>)}
            </select>
          </label>
          <label>Date
            <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
          </label>
        </div>
        <ScreeningCards screenings={screenings} onNavigate={onNavigate} emptyMessage="No screenings are available for this movie." />
      </section>
      {error && <p className="error-message">{error}</p>}
    </main>
  );
}

export function RepertoirePage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [cityId, setCityId] = useState('');
  const [cinemaId, setCinemaId] = useState('');
  const [movieId, setMovieId] = useState('');
  const [date, setDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      apiRequest<CityResponse[]>('/api/cities'),
      apiRequest<CinemaResponse[]>('/api/cinemas'),
      apiRequest<MovieResponse[]>('/api/movies'),
    ])
      .then(([nextCities, nextCinemas, nextMovies]) => {
        setCities(nextCities);
        setCinemas(nextCinemas);
        setMovies(nextMovies);
      })
      .catch(() => setError('Unable to load repertoire filters. Please try again.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const params = new URLSearchParams();
    if (cityId) params.set('cityId', cityId);
    if (cinemaId) params.set('cinemaId', cinemaId);
    if (movieId) params.set('movieId', movieId);
    if (date) params.set('date', date);
    const query = params.toString();
    apiRequest<ScreeningResponse[]>(`/api/screenings${query ? `?${query}` : ''}`)
      .then((response) => {
        setScreenings(response);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [cityId, cinemaId, movieId, date]);

  const availableCinemas = cityId ? cinemas.filter((cinema) => cinema.city.id === Number(cityId)) : cinemas;

  return (
    <main className="page">
      <h1>Repertoire</h1>
      <div className="filters-grid">
        <label>City<select value={cityId} onChange={(event) => setCityId(event.target.value)}>
          <option value="">All cities</option>
          {cities.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}
        </select></label>
        <label>Cinema<select value={cinemaId} onChange={(event) => setCinemaId(event.target.value)}>
          <option value="">All cinemas</option>
          {availableCinemas.map((cinema) => <option key={cinema.id} value={cinema.id}>{cinema.name}</option>)}
        </select></label>
        <label>Date<input type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
        <label>Movie<select value={movieId} onChange={(event) => setMovieId(event.target.value)}>
          <option value="">All movies</option>
          {movies.map((movie) => <option key={movie.id} value={movie.id}>{movie.title}</option>)}
        </select></label>
      </div>
      {loading && <p className="page-message">Loading screenings...</p>}
      {!loading && <ScreeningCards screenings={screenings} onNavigate={onNavigate} emptyMessage="No screenings match the selected filters." showMovieTitle />}
      {error && <p className="error-message">{error}</p>}
    </main>
  );
}

export function SeatSelectionPage({
  screeningId,
  currentUser,
  onNavigate,
}: {
  screeningId: number;
  currentUser: UserResponse | null;
  onNavigate: (path: string) => void;
}) {
  const [screening, setScreening] = useState<ScreeningResponse | null>(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    apiRequest<ScreeningResponse>(`/api/screenings/${screeningId}`)
      .then(setScreening)
      .catch((err) => setMessage(getErrorMessage(err)));
  }, [screeningId]);

  return (
    <main className="page">
      <h1>Select Seats</h1>
      {screening && (
        <SeatMap
          screening={screening}
          currentUser={currentUser}
          onReserved={() => onNavigate('/reservations')}
        />
      )}
      {message && <p className="error-message">{message}</p>}
    </main>
  );
}

function ScreeningCards({
  screenings,
  onNavigate,
  emptyMessage,
  showMovieTitle = false,
}: {
  screenings: ScreeningResponse[];
  onNavigate: (path: string) => void;
  emptyMessage: string;
  showMovieTitle?: boolean;
}) {
  const groups = screenings.reduce<ScreeningGroup[]>((acc, screening) => {
    const date = screening.startTime.slice(0, 10);
    const key = `${date}-${screening.cinemaId}`;
    const existing = acc.find((group) => group.key === key);
    if (existing) {
      existing.screenings.push(screening);
      return acc;
    }
    acc.push({
      key,
      cityName: screening.cityName,
      cinemaName: screening.cinemaName,
      date,
      screenings: [screening],
    });
    return acc;
  }, []);

  return (
    <div className="screening-list">
      {screenings.length === 0 ? (
        <p className="empty-state">{emptyMessage}</p>
      ) : (
        groups.map((group) => (
          <section className="screening-group" key={group.key}>
            <div className="screening-group-heading">
              <div>
                <p className="eyebrow">{group.cityName}</p>
                <h3>{group.cinemaName}</h3>
              </div>
              <strong>{formatDate(group.date)}</strong>
            </div>
            <div className="screening-time-grid">
              {group.screenings
                .sort((first, second) => new Date(first.startTime).getTime() - new Date(second.startTime).getTime())
                .map((screening) => (
                  <button className="screening-time-card" type="button" key={screening.id} onClick={() => onNavigate(`/screenings/${screening.id}/seats`)}>
                    {showMovieTitle && <span>{screening.movieTitle}</span>}
                    <strong>{formatTime(screening.startTime)}</strong>
                    <span>{screening.hallName}</span>
                    <small>{screening.ticketPrice} RSD</small>
                  </button>
                ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}

type ScreeningGroup = {
  key: string;
  cityName: string;
  cinemaName: string;
  date: string;
  screenings: ScreeningResponse[];
};

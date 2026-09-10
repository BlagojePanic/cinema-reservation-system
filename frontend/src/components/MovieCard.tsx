import { MovieResponse } from '../api';

type MovieCardProps = {
  movie: MovieResponse;
  onOpen: (movieId: number) => void;
};

export function MovieCard({ movie, onOpen }: MovieCardProps) {
  return (
    <button className="movie-card" type="button" onClick={() => onOpen(movie.id)}>
      <Poster movie={movie} />
      <span className="movie-card-title">{movie.title}</span>
      <span>{movie.genre}</span>
      <span>{movie.durationMinutes} min</span>
      {movie.ageRating && <span className="movie-age-rating">{movie.ageRating}</span>}
    </button>
  );
}

export function Poster({ movie }: { movie: MovieResponse }) {
  if (movie.posterUrl) {
    return <img src={movie.posterUrl} alt={`${movie.title} poster`} />;
  }

  return <span className="poster-placeholder">{movie.title.charAt(0).toUpperCase()}</span>;
}

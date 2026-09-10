const TOKEN_KEY = 'cryptocinema_token';

export type UserRole = 'USER' | 'ADMIN';

export type UserResponse = {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
};

export type AuthResponse = {
  token: string;
  tokenType: 'Bearer';
  user: UserResponse;
};

export type CityResponse = {
  id: number;
  name: string;
};

export type CinemaResponse = {
  id: number;
  name: string;
  address: string;
  city: CityResponse;
};

export type HallResponse = {
  id: number;
  name: string;
  cinemaId: number;
  cinemaName: string;
};

export type SeatResponse = {
  id: number;
  rowLabel: string;
  seatNumber: number;
  hallId: number;
};

export type MovieResponse = {
  id: number;
  title: string;
  description: string;
  genre: string;
  durationMinutes: number;
  ageRating: string | null;
  director: string | null;
  releaseDate: string | null;
  posterUrl: string | null;
  trailerUrl: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
};

export type ScreeningResponse = {
  id: number;
  movieId: number;
  movieTitle: string;
  hallId: number;
  hallName: string;
  cinemaId: number;
  cinemaName: string;
  cityId: number;
  cityName: string;
  startTime: string;
  ticketPrice: number;
};

export type ScreeningSeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';

export type ScreeningSeatResponse = {
  screeningSeatId: number;
  seatId: number;
  rowLabel: string;
  seatNumber: number;
  status: ScreeningSeatStatus;
  heldByCurrentUser: boolean;
  holdExpiresAt: string | null;
};

export type ReservationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';

export type PaymentMethod = 'CARD_SIMULATION' | 'CRYPTO';

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export type TicketStatus = 'VALID' | 'USED';

export type PaymentResponse = {
  paymentId: number;
  reservationId: number;
  amount: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  reference: string;
  createdAt: string;
  completedAt: string | null;
  cryptoCurrency: string | null;
  cryptoAmount: number | null;
  exchangeRate: number | null;
  network: string | null;
  chainId: number | null;
  walletAddress: string | null;
  transactionHash: string | null;
};

export type ReservationResponse = {
  reservationId: number;
  status: ReservationStatus;
  movieTitle: string;
  screeningId: number;
  screeningStartTime: string;
  cinemaName: string;
  hallName: string;
  seatLabels: string[];
  ticketPrice: number;
  totalAmount: number;
  createdAt: string;
  expiresAt: string;
  payment: PaymentResponse | null;
};

export type CryptoPaymentPrepareResponse = {
  reservationId: number;
  paymentId: number;
  merchantAddress: string;
  network: string;
  chainId: number;
  cryptoCurrency: string;
  cryptoAmount: number;
  amountRsd: number;
  expiresAt: string;
};

export type AdminReservationResponse = {
  reservationId: number;
  userId: number;
  userEmail: string;
  movieTitle: string;
  screeningId: number;
  startTime: string;
  cinemaName: string;
  hallName: string;
  seats: string[];
  totalAmount: number;
  reservationStatus: ReservationStatus;
  createdAt: string;
  expiresAt: string;
  latestPaymentStatus: PaymentStatus | null;
  latestPaymentMethod: PaymentMethod | null;
  latestPaymentReference: string | null;
  latestPaymentTransactionHash: string | null;
};

export type TicketResponse = {
  ticketCode: string;
  ticketStatus: TicketStatus;
  movieTitle: string;
  cinemaName: string;
  hallName: string;
  screeningStartTime: string;
  seats: string[];
  totalAmount: number;
  reservationId: number;
  createdAt: string;
  usedAt: string | null;
};

export type AdminTicketResponse = TicketResponse & {
  reservationStatus: ReservationStatus;
  userEmail: string;
};

export type TicketValidationResponse = {
  accepted: boolean;
  message: string;
  ticket: AdminTicketResponse;
};

export type ApiError = {
  message: string;
  status?: number;
};

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();

  if (!headers.has('Content-Type') && options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Keep the default message when the response has no JSON body.
    }
    throw { message, status: response.status } satisfies ApiError;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export async function apiBlob(path: string): Promise<Blob> {
  const headers = new Headers();
  const token = getToken();

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, { headers });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Keep the default message when the response has no JSON body.
    }
    throw { message, status: response.status } satisfies ApiError;
  }

  return response.blob();
}

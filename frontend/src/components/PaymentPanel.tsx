import { FormEvent, useState } from 'react';
import { apiRequest, CryptoPaymentPrepareResponse, PaymentResponse, ReservationResponse } from '../api';
import { decimalEthToWeiHex, getErrorMessage, shortAddress } from '../utils/format';
import { AppModal } from './AppModal';

type CryptoUiStatus = 'READY' | 'WALLET_CONNECTED' | 'TRANSACTION_SENT' | 'WAITING_CONFIRMATION' | 'SUCCESS' | 'FAILED';

type CryptoUiState = {
  status: CryptoUiStatus;
  message: string;
  walletAddress?: string;
  transactionHash?: string;
  prepare?: CryptoPaymentPrepareResponse;
};

type EthereumProvider = {
  request<T = unknown>(args: { method: string; params?: unknown[] }): Promise<T>;
};

declare global {
  interface Window {
    ethereum?: EthereumProvider;
  }
}

type PaymentPanelProps = {
  reservation: ReservationResponse;
  onChanged: () => void;
};

type PaymentStep = 'METHOD' | 'CARD' | 'METAMASK';

export function PaymentPanel({ reservation, onChanged }: PaymentPanelProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<PaymentStep>('METHOD');
  const [form, setForm] = useState({ pan: '', holder: '', validUntil: '', securityCode: '' });
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);
  const [cryptoState, setCryptoState] = useState<CryptoUiState | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  function closeModal() {
    if (busy) return;
    setOpen(false);
    setStep('METHOD');
    setForm({ pan: '', holder: '', validUntil: '', securityCode: '' });
    setFormError('');
    setCryptoState(null);
    setMessage('');
    setError('');
  }

  async function payCard(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError('');
    const digits = form.pan.replace(/\D/g, '');
    const validUntil = form.validUntil.trim();
    const holder = form.holder.trim();
    const securityCode = form.securityCode.trim();

    if (digits.length !== 16) {
      setFormError('Card number must contain 16 digits.');
      return;
    }
    if (!holder) {
      setFormError('Cardholder name is required.');
      return;
    }
    if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(validUntil)) {
      setFormError('Expiry date must use MM/YY format.');
      return;
    }
    if (!/^\d{3}$/.test(securityCode)) {
      setFormError('CVV must contain 3 digits.');
      return;
    }

    setBusy(true);
    setError('');
    setMessage('');
    try {
      const payment = await apiRequest<PaymentResponse>(`/api/reservations/${reservation.reservationId}/payment`, {
        method: 'POST',
        body: JSON.stringify({ method: 'CARD_SIMULATION', simulateSuccess: true }),
      });
      setMessage(payment.status === 'SUCCESS' ? 'Payment successful.' : 'Payment failed. You can try again.');
      onChanged();
      if (payment.status === 'SUCCESS') {
        setOpen(false);
        setStep('METHOD');
        setForm({ pan: '', holder: '', validUntil: '', securityCode: '' });
      }
    } catch (err) {
      setError(getErrorMessage(err));
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  async function payWithMetaMask() {
    setBusy(true);
    setError('');
    setMessage('');
    setCryptoState({ status: 'READY', message: 'Preparing MetaMask payment.' });

    try {
      const ethereum = window.ethereum;
      if (!ethereum) {
        setCryptoState({ status: 'FAILED', message: 'MetaMask is not installed.' });
        return;
      }

      const accounts = await ethereum.request<string[]>({ method: 'eth_requestAccounts' });
      const walletAddress = accounts[0];
      setCryptoState({ status: 'WALLET_CONNECTED', message: 'Wallet connected.', walletAddress });

      const chainId = await ethereum.request<string>({ method: 'eth_chainId' });
      if (chainId.toLowerCase() !== '0xaa36a7') {
        await ethereum.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0xaa36a7' }],
        });
      }

      const prepare = await apiRequest<CryptoPaymentPrepareResponse>(
        `/api/reservations/${reservation.reservationId}/crypto-payment/prepare`,
        { method: 'POST' },
      );
      setCryptoState({
        status: 'WALLET_CONNECTED',
        message: `Ready to send ${prepare.cryptoAmount} ${prepare.cryptoCurrency}.`,
        walletAddress,
        prepare,
      });

      const transactionHash = await ethereum.request<string>({
        method: 'eth_sendTransaction',
        params: [{
          from: walletAddress,
          to: prepare.merchantAddress,
          value: decimalEthToWeiHex(prepare.cryptoAmount),
        }],
      });
      const waitingState = {
        status: 'WAITING_CONFIRMATION' as const,
        message: 'Transaction sent. Waiting for backend verification.',
        walletAddress,
        transactionHash,
        prepare,
      };
      setCryptoState(waitingState);
      await confirmCrypto(waitingState);
    } catch (err) {
      setError(getErrorMessage(err));
      setCryptoState({ status: 'FAILED', message: getErrorMessage(err) });
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  async function confirmCrypto(state = cryptoState) {
    if (!state?.prepare || !state.transactionHash || !state.walletAddress) {
      setError('No pending crypto transaction is available for verification.');
      return;
    }

    setBusy(true);
    setError('');
    try {
      const payment = await apiRequest<PaymentResponse>(
        `/api/reservations/${reservation.reservationId}/crypto-payment/confirm`,
        {
          method: 'POST',
          body: JSON.stringify({
            paymentId: state.prepare.paymentId,
            transactionHash: state.transactionHash,
            walletAddress: state.walletAddress,
          }),
        },
      );
      setCryptoState({
        ...state,
        status: payment.status === 'SUCCESS' ? 'SUCCESS' : 'WAITING_CONFIRMATION',
        message: payment.status === 'SUCCESS'
          ? 'Payment successful.'
          : 'Transaction is still pending. Try verification again in a few seconds.',
      });
      setMessage(payment.status === 'SUCCESS' ? 'Payment successful.' : '');
      onChanged();
      if (payment.status === 'SUCCESS') {
        setOpen(false);
        setStep('METHOD');
        setCryptoState(null);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      setCryptoState({ ...state, status: 'FAILED', message: getErrorMessage(err) });
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button className="accent-button" type="button" onClick={() => setOpen(true)}>Buy ticket</button>
      {open && (
        <AppModal
          title={step === 'METHOD' ? 'Choose payment method' : step === 'CARD' ? 'Card checkout' : 'MetaMask checkout'}
          onClose={closeModal}
          showClose={step !== 'METHOD'}
        >
          {step === 'METHOD' && (
            <>
              <PaymentSummary reservation={reservation} />
              <div className="payment-choice-grid">
                <button className="payment-choice" type="button" onClick={() => setStep('CARD')} disabled={busy}>
                  <span aria-hidden="true">💳</span>
                  <strong>Card</strong>
                  <small>Secure-looking checkout simulation.</small>
                </button>
                <button className="payment-choice" type="button" onClick={() => setStep('METAMASK')} disabled={busy}>
                  <span aria-hidden="true">🦊</span>
                  <strong>MetaMask</strong>
                  <small>Sepolia ETH with backend verification.</small>
                </button>
              </div>
              <div className="modal-footer-inline"><button type="button" onClick={closeModal} disabled={busy}>Cancel</button></div>
            </>
          )}

          {step === 'CARD' && (
            <form className="checkout-form" onSubmit={payCard}>
              <PaymentSummary reservation={reservation} />
              <label>Card number
                <input
                  inputMode="numeric"
                  autoComplete="off"
                  value={form.pan}
                  onChange={(event) => setForm((current) => ({ ...current, pan: event.target.value }))}
                  placeholder="1234 5678 9012 3456"
                />
              </label>
              <label>Cardholder name
                <input
                  autoComplete="off"
                  value={form.holder}
                  onChange={(event) => setForm((current) => ({ ...current, holder: event.target.value }))}
                  placeholder="Alex Example"
                />
              </label>
              <div className="checkout-two-column">
                <label>Expiry date
                  <input
                    autoComplete="off"
                    value={form.validUntil}
                    onChange={(event) => setForm((current) => ({ ...current, validUntil: event.target.value }))}
                    placeholder="MM/YY"
                  />
                </label>
                <label>CVV
                  <input
                    inputMode="numeric"
                    autoComplete="off"
                    value={form.securityCode}
                    onChange={(event) => setForm((current) => ({ ...current, securityCode: event.target.value }))}
                    placeholder="123"
                  />
                </label>
              </div>
              {formError && <p className="error-message">{formError}</p>}
              {error && <p className="error-message">{error}</p>}
              <div className="actions">
                <button type="button" onClick={() => setStep('METHOD')} disabled={busy}>Back</button>
                <button className="accent-button" type="submit" disabled={busy}>Pay {reservation.totalAmount} RSD</button>
              </div>
            </form>
          )}

          {step === 'METAMASK' && (
            <div className="metamask-panel">
              <PaymentSummary reservation={reservation} />
              <div className="crypto-status">
                <span>Network: Sepolia</span>
                {cryptoState?.walletAddress ? <span>Connected wallet: {shortAddress(cryptoState.walletAddress)}</span> : <span>Connected wallet: Not connected</span>}
                {cryptoState?.prepare ? (
                  <>
                    <span>Amount in ETH: {cryptoState.prepare.cryptoAmount}</span>
                    <span>Approximate RSD amount: {cryptoState.prepare.amountRsd}</span>
                    <span>Merchant: {shortAddress(cryptoState.prepare.merchantAddress)}</span>
                  </>
                ) : (
                  <span>Approximate RSD amount: {reservation.totalAmount}</span>
                )}
                {cryptoState && <span>Status: {cryptoState.status}</span>}
                {cryptoState && <span>{cryptoState.message}</span>}
                {cryptoState?.transactionHash && (
                  <a href={`https://sepolia.etherscan.io/tx/${cryptoState.transactionHash}`} target="_blank" rel="noreferrer">
                    Sepolia transaction
                  </a>
                )}
              </div>
              {message && <p className="status-message">{message}</p>}
              {error && <p className="error-message">{error}</p>}
              <div className="actions">
                <button type="button" onClick={() => setStep('METHOD')} disabled={busy}>Back</button>
                {cryptoState?.transactionHash && cryptoState.status === 'WAITING_CONFIRMATION' ? (
                  <button className="accent-button" type="button" onClick={() => confirmCrypto()} disabled={busy}>Verify transaction</button>
                ) : (
                  <button className="accent-button" type="button" onClick={payWithMetaMask} disabled={busy}>
                    {cryptoState?.walletAddress ? 'Send with MetaMask' : 'Connect MetaMask'}
                  </button>
                )}
              </div>
            </div>
          )}
        </AppModal>
      )}
    </>
  );
}

function PaymentSummary({ reservation }: { reservation: ReservationResponse }) {
  return (
    <div className="checkout-summary compact-summary">
      <span>Movie: {reservation.movieTitle}</span>
      <span>Seats: {reservation.seatLabels.join(', ')}</span>
      <strong>Total: {reservation.totalAmount} RSD</strong>
    </div>
  );
}

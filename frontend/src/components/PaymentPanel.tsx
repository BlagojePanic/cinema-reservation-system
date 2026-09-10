import { useState } from 'react';
import { apiRequest, CryptoPaymentPrepareResponse, PaymentResponse } from '../api';
import { decimalEthToWeiHex, getErrorMessage, shortAddress } from '../utils/format';

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
  reservationId: number;
  onChanged: () => void;
};

export function PaymentPanel({ reservationId, onChanged }: PaymentPanelProps) {
  const [busy, setBusy] = useState(false);
  const [cryptoState, setCryptoState] = useState<CryptoUiState | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function payCard(simulateSuccess: boolean) {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const payment = await apiRequest<PaymentResponse>(`/api/reservations/${reservationId}/payment`, {
        method: 'POST',
        body: JSON.stringify({ method: 'CARD_SIMULATION', simulateSuccess }),
      });
      setMessage(payment.status === 'SUCCESS' ? 'Payment successful.' : 'Payment failed. You can try again.');
      onChanged();
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
        `/api/reservations/${reservationId}/crypto-payment/prepare`,
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
        `/api/reservations/${reservationId}/crypto-payment/confirm`,
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
    } catch (err) {
      setError(getErrorMessage(err));
      setCryptoState({ ...state, status: 'FAILED', message: getErrorMessage(err) });
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="payment-methods">
      <section className="payment-method">
        <h3>Card Simulation</h3>
        <p>Use this testing method to simulate card authorization.</p>
        <div className="actions">
          <button type="button" onClick={() => payCard(true)} disabled={busy}>Simulate SUCCESS</button>
          <button type="button" onClick={() => payCard(false)} disabled={busy}>Simulate FAILURE</button>
        </div>
      </section>

      <section className="payment-method">
        <h3>Crypto / MetaMask</h3>
        <p>Sepolia ETH testnet payment with backend transaction verification.</p>
        <button type="button" onClick={payWithMetaMask} disabled={busy}>Connect MetaMask</button>
        {cryptoState && (
          <div className="crypto-status">
            <span>Status: {cryptoState.status}</span>
            <span>{cryptoState.message}</span>
            {cryptoState.walletAddress && <span>Wallet: {shortAddress(cryptoState.walletAddress)}</span>}
            {cryptoState.prepare && (
              <>
                <span>Network: {cryptoState.prepare.network}</span>
                <span>Amount RSD: {cryptoState.prepare.amountRsd}</span>
                <span>Amount ETH: {cryptoState.prepare.cryptoAmount}</span>
                <span>Merchant: {shortAddress(cryptoState.prepare.merchantAddress)}</span>
              </>
            )}
            {cryptoState.transactionHash && (
              <a href={`https://sepolia.etherscan.io/tx/${cryptoState.transactionHash}`} target="_blank" rel="noreferrer">
                Sepolia transaction
              </a>
            )}
            {cryptoState.transactionHash && cryptoState.status === 'WAITING_CONFIRMATION' && (
              <button type="button" onClick={() => confirmCrypto()} disabled={busy}>Verify transaction</button>
            )}
          </div>
        )}
      </section>

      {message && <p className="status-message">{message}</p>}
      {error && <p className="error-message">{error}</p>}
    </div>
  );
}

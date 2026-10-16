import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage.jsx';
import { api } from '../api/client.js';

describe('LoginPage', () => {
  it('disables submit until both fields are filled', async () => {
    render(<LoginPage onLogin={() => {}} />);
    const submit = screen.getByTestId('login-submit');
    expect(submit).toBeDisabled();
    await userEvent.type(screen.getByTestId('login-username'), 'driver1');
    await userEvent.type(screen.getByTestId('login-password'), 'pw');
    expect(submit).toBeEnabled();
  });

  it('shows a friendly message on 401', async () => {
    vi.spyOn(api, 'login').mockRejectedValue(Object.assign(new Error('x'), { status: 401 }));
    render(<LoginPage onLogin={() => {}} />);
    await userEvent.type(screen.getByTestId('login-username'), 'driver1');
    await userEvent.type(screen.getByTestId('login-password'), 'bad');
    await userEvent.click(screen.getByTestId('login-submit'));
    expect(await screen.findByTestId('login-error')).toHaveTextContent('Invalid username or password');
  });

  it('shows the server\'s explanation when sign-in is locked (429)', async () => {
    vi.spyOn(api, 'login').mockRejectedValue(Object.assign(
      new Error('Too many failed sign-in attempts. Try again in 5 minutes.'), { status: 429 }));
    render(<LoginPage onLogin={() => {}} />);
    await userEvent.type(screen.getByTestId('login-username'), 'driver1');
    await userEvent.type(screen.getByTestId('login-password'), 'bad');
    await userEvent.click(screen.getByTestId('login-submit'));
    expect(await screen.findByTestId('login-error'))
      .toHaveTextContent('Too many failed sign-in attempts. Try again in 5 minutes.');
  });
});

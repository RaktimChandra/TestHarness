As a security officer, I want repeated failed sign-ins to lock an account for a while, so that passwords cannot be guessed by trying many of them.
After five failed sign-ins for one username within fifteen minutes, sign-in for that username is refused for five minutes with TOO_MANY_ATTEMPTS, even with the right password. Other accounts are not affected.

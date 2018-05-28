# Unit testing framework

## Examples

    PTest.describe 'User model' do
      it 'allows logging in with the correct password', do
        user = User.create email: '...', password: '1234'

        expect LoginService.login(email: '...', password: '1234') == user
      end

      it '
    end

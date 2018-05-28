# Concurrency

## Auto async

    async do
      # `user` and `post` are loaded at the same time (asynchronously)
      user = User.find(...)
      post = Post.find(...)
    end

    likes = Like.where(user: user, post: post)

The above is equivalent to:

    user: User
    post: Post

    async_group do |group|
      group.schedule { user = User.find(...) }
      group.schedule { post = Post.find(...) }
    end

    likes = Like.where(user: user, post: post)

## Async groups

    def accept_request: Connection
      # ...
    end

    def process_request(connection: Connection)
      # ...
    end

    def should_shutdown: Bool
      # ...
    end

    async_group do |group|
      while true
        break if should_shutdown

        request = accept_request

        group.schedule { process_request request }
      end

      print 'Waiting for existing requests to finish'
    end

    # Reaching this means all tasks inside the async group is finished
    print 'Exited'

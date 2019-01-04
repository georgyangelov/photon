# Asyncronicity

## Streams

Due to the implicit asynchronicity of the language, any stream can be both synchronous and asynchronous:

```ruby
def process(stream)
  stream
    |.map { _1 * _1 }
    |.select { _.divided_by? 2 }
    |.take 10
end

sync_results  = Stream.iterate 1, { _ + 1 } | process
async_results = FileSystem.directory("~")
  |.files
  |.map { _.size }
  | process
```

### Q: What happens when we iterate such a stream?

```ruby
sync_results.each { IO.puts _ }
IO.puts 'After'

async_results.each { IO.puts _ }
IO.puts 'After'
```

In both cases, `After` will be printed last, after the whole stream has been processed.

### Q: What if the stream is infinite (never closed) and we want to keep running the code?

```ruby
# This runs each statement in the lambda in its own async context
async do
  async_results.each { IO.puts _ }

  IO.puts 'After'
end

# The above is just syntactic sugar for this code
# Q: Should there be the above? It is not clear if the above loop (the `each` statement) is ran async as a whole or each operation in it is.
async do
  # Here `this` is a nested async context manager object (has method `run` which doesn't wait for the results)
  run { async_results.each { IO.puts _ } }
  run { IO.puts 'After' }
end

IO.puts 'Exiting...'
```

Another option?

```ruby
results = async do
  run { async_results.each { IO.puts _ } }
end

IO.puts 'After'

results.await
```

Which one is better if such contexts are nested? The first one seems cleaner and implicitly waits for all async tasks to finish.
This is good because then at the top level the program will only exit once all of its async tasks are done. In the second case, it will finish immediately unless explicitly awaited.

### Example: Running a bunch of jobs asynchronously

```ruby
def main
  socket = ListenerSocket.new("0.0.0.0", "80")

  async do
    run { HTTPServer.new(socket).run }
    run { socket.run }
  end
end

module HTTPServer
  self: interface
    listener: ListenerSocket
  end

  static
    def new(socket: ListenerSocket)
      $HTTPServer{
        listener: socket
      }
    end
  end

  def run
    # This will exit when both:
    # 1. The `new_connections` stream is closed (has no more elements)
    # 2. All elements in `new_connections` have been ended
    # 3. All connections have been handled
    #
    # 1. and 2. mean that the lambda passed to `async` has finished executing.
    # 3. means that all async contexts spawned by this block have finished executing.
    async do
      listener.new_connections.each do |connection|
        run { handle_connection connection }
      end
    end
  end

  def handle_connection(connection: Connection)
    connection.requests.each do |request|
      connection.send_response handle_request(request)
    end
  end

  def handle_request(request: Request): Response
    find_handler(headers)(request)
  end

  def find_handler(headers: Headers): Fn(Request, Response)
    # ...
  end
end

module ListenerSocket
  self: interface
    listener: Socket,
    new_sockets: Stream(Socket),
    new_connections: Stream(Connection)
  end

  def run
    async do
      new_sockets
        |.map { Connection.new _ }
        |.tap { |connection| run { connection.run } }
        |.pipe_to new_connections
    end
  end
end

module Connection
  self: interface
    socket: Socket,
    requests: Stream(Request)
  end

  static
    def new(socket: Socket)
      $Connection{
        socket: socket,
        requests: Stream(Request).new
      }
    end
  end

  def run
    Request.read_from(socket).pipe_to(requests)
  end
end

module Request
  # ...
end
```

### Q: Can we do something about the all-encompasing presence of the `run` methods?

Options:

- Do nothing, it will not be that all-encompasing
- RAII
    - `run` is started at the end of a function, then is waited until it finishes.
    - Won't work in all cases and will need to have a way to be disabled.

### Example of a task queue that can be used to offload async contexts without waiting for them

```ruby
module AsyncTaskManager
  self: interface
    - tasks: Stream(Fn())
  end

  static
    def new
      $AsyncTaskManager{
        tasks: Stream(Fn()).new
      }
    end
  end

  def run
    async do |c|
      tasks.each { |task| c.run { task() } }
    end
  end

  def stop
    tasks.close
  end

  def dispatch(task: Fn())
    tasks.push(task)
  end
end

manager = AsyncTaskManager.new

async do |c|
  c.run { use_manager }
  c.run { manager.run }
end

def use_manager
  manager.dispatch { IO.puts 'Running A' }
  manager.dispatch { IO.puts 'Running B' }

  IO.puts 'This will probably be printed first as the dispatch calls are not awaited in this context'

  manager.stop
end
```

### Example of implementing an agent (as in Clojure or Elixir)

```ruby
module Agent(State)
  self: interface
    - state: Ref(State)
    - tasks: Stream(Fn(State, State))
  end

  static
    def new(state: State)
      $Agent{ state }
    end
  end

  def run
    tasks.each { |task| state.set task(state.get) }
  end

  def stop
    tasks.close
  end

  # TODO: Can this wait for the state update to be finished?
  def update(fn: Fn(State, State))
    tasks.push fn
  end

  def get: State
    state.get
  end
end

agent = Agent(Int).new(0)

async do |c|
  # Could this be a solution to the `stop` problem? `run_with` will call `run` and `stop` automatically.
  # c.run_with agent, { use_agent }
  c.run { use_agent; agent.stop }
  c.run { agent.run }
end

def use_agent
  agent.update { _ + 1 }
  agent.update { _ + 1 }
  agent.update { _ + 1 }

  # Some time after...
  agent.get #=> 3
end
```

### Q: How to handle this stopping of async contexts gracefully? How would they know when they've stopped?

1. Have a stop method as in the above that closes streams and performs clean-ups.
    - How would this work with the garbage collection?
    - Could this be called automatically from above? Maybe if the `run` and `stop` methods are special-cased? Special-casing seems excessive and would introduce complexity.

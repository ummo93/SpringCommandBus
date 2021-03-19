# SpringCommandBus
Simple command bus for web and CLI applications

[![Repo](https://jitpack.io/v/ummo93/SpringCommandBus.svg)](https://jitpack.io/#ummo93/SpringCommandBus)

#### Example of usage
##### Let's implement main command bus

```java
@Component
public class CommandBus extends BaseCommandBus {

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Override
    protected void beforeExecuteCommand(String commandId, BaseCommand command) {
        System.out.println("=======================================================");
        System.out.println("    Start executing command `" + commandId + "` in " + (getIsCliMode() ? "CLI": "WEB") + " mode");
        System.out.println("=======================================================");
    }

    @Override
    protected void onCommandException(String commandId, BaseCommand command, Exception exception) {
        System.err.println("EXCEPTION HAPPENS:");
        System.err.println(exception.getMessage());
    }

    @Override
    protected void onCommandExecuted(String commandId, BaseCommand command, CommandResult result) {
        System.out.println("=======================================================");
        if (result.isPresent()) {
            System.out.println("                 COMMAND OUTPUT");
            System.out.println(result.get());
            System.out.println("=======================================================");
        }
    }

    @Override
    protected void afterHandleFinally() {
        // Close context if command executes over CLI
        if(getIsCliMode()) {
            applicationContext.close();
        }
    }
}
```

##### Go ahead. Create first Command and Command Handler

- GetAllCustomersCommand.java:
```java
public class GetAllCustomersCommand extends BaseCommand {
}
```

- GetAllCustomersCommandHandler:
```java
@Command(id="getAllCustomers")
public class GetAllCustomersCommandHandler extends BaseCommandHandler<GetAllCustomersCommand, List<CustomerDTO>> {

    @Autowired
    private CustomerRepository customerRepository;

    @Transactional
    @Override
    public void handle(GetAllCustomersCommand command, CommandResult<List<CustomerDTO>> out) throws CustomerSpecificException {
        var result = customerRepository.findAll().stream().map(CustomerMapper::map).collect(Collectors.toList());
        out.put(result);
    }
}
```


##### Let's take a look at the client code (CustomersController for example)
```java
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private CommandBus commandBus;

    public CustomerController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping
    @ResponseBody
    public List<CustomerDTO> getAllCustomers() {
        var getCustomersCommand = new GetAllCustomersCommand();

        try {
            CommandResult<List<CustomerDTO>> result = commandBus.handle(getCustomersCommand);

            if (!result.isPresent()) {
                System.err.println("Shit happens...");
                return List.of();
            }

            return result.get();
        } catch (CustomerSpecificException e) {
            System.err.println("Shit happens...");
            return List.of();
        }
    }
}
```

### Description and architectural reasons to use this
Info about hexagonal architecture concepts should be here soon...

### How to use it in CLI mode
Should be here soon too...

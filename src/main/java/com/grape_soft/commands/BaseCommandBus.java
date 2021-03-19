package com.grape_soft.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ClassUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public abstract class BaseCommandBus implements ApplicationListener<ContextRefreshedEvent> {

    ConfigurableApplicationContext ctx;
    ApplicationArguments appArgs;

    @Autowired
    BaseCommandBus(ConfigurableApplicationContext ctx, ApplicationArguments appArgs) {
        this.ctx = ctx;
        this.appArgs = appArgs;
    }

    private final Map<Class, BaseCommandHandler> cachedCommands = new HashMap<>();
    private final Map<String, Class> cachedCommandsById = new HashMap<>();
    private boolean isCliMode = false;

    public boolean getIsCliMode() {
        return isCliMode;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        cacheAllCommands();
        String[] args = appArgs.getSourceArgs();
        isCliMode = getIsApplicationCommandShellModeByArgs(args);
        if (!isCliMode) return;
        handleCliParametersIfExists(args);
    }

    /**
     * Handle command for client's code in WEB mode
     * @param command
     * @param <T>
     * @return CommandResult<T>
     */
    public <T, U extends BaseCommand> CommandResult<T> handle(U command) throws Exception {
        if (!cachedCommands.containsKey(command.getClass())) {
            return null;
        }

        BaseCommandHandler handler = cachedCommands.get(command.getClass());

        this.beforeExecuteCommand(handler.getCommandId(), command);

        CommandResult<T> result = new CommandResult<>();
        try {
            handler.handle(command, result);
            this.onCommandExecuted(handler.commandId, command, result);
        } catch (Exception e) {
            this.onCommandException(handler.commandId, command, e);
            throw e;
        } finally {
            afterHandleFinally();
        }

        return result;
    }

    /**
     * To overriding
     * @param commandId
     * @param command
     */
    protected void beforeExecuteCommand(String commandId, BaseCommand command) {
        // To overriding
    }

    /**
     * To overriding
     * @param commandId
     * @param command
     * @param result
     */
    protected <T> void onCommandExecuted(String commandId, BaseCommand command, CommandResult<T> result) {
        // To overriding
    }

    /**
     * To overriding
     * @param commandId
     * @param command
     * @param exception
     */
    protected void onCommandException(String commandId, BaseCommand command, Exception exception) {
        // To overriding
    }

    /**
     * // To overriding
     */
    protected void afterHandleFinally() {
        // To overriding
    }

    private void cacheAllCommands() {
        Map<String, Object> beans = this.ctx.getBeansWithAnnotation(Command.class);
        for (Object bean: beans.values()) {
            String name = bean.getClass().getName();
            Class commandClass;

            if (name.matches(".*" + Pattern.quote(ClassUtils.CGLIB_CLASS_SEPARATOR) +".*CGLIB.*")) { // for class with Spring enhancer proxy
                commandClass = (Class) ((ParameterizedType) bean.getClass().getSuperclass().getGenericSuperclass()).getActualTypeArguments()[0];
            } else {
                commandClass = (Class) ((ParameterizedType) bean.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            }

            BaseCommandHandler handler = (BaseCommandHandler) bean;
            cachedCommands.put(commandClass, handler);
            cachedCommandsById.put(handler.getCommandId(), commandClass);
        }
    }

    private void handleCliParametersIfExists(String[] args) {
        String commandId = getCommandFromCliArgs(args);
        String[] commandParams = getCommandParamsFromCliArgs(args);
        this.invokeCommandByIdWithParams(commandId, commandParams);
    }

    private String getCommandFromCliArgs(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg.matches("-command=.*"))
                .map(arg -> arg.replace("-command=", ""))
                .findFirst()
                .get();
    }

    private String[] getCommandParamsFromCliArgs(String[] args) {
        return Arrays.stream(args)
                .sorted((first, second) -> first.matches("-command=.*") ? 1: 0)
                .skip(1)
                .filter(arg -> arg.matches("-arg=.*"))
                .map(arg -> arg.replaceAll("-arg=", ""))
                .toArray(String[]::new);
    }

    private boolean getIsApplicationCommandShellModeByArgs(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.matches("-command=.*"));
    }

    private BaseCommand instantiateAccordingToTypes(String[] rawArgs, Constructor<?>[] constructors) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        ArrayList<Object> typedArgs = new ArrayList<>();
        Optional<Constructor<?>> firstSuitableConstructor = Stream.of(constructors)
                .filter((constructor) -> {
                    boolean isMatch = false;
                    try {
                        Class<?>[] paramTypes = constructor.getParameterTypes();
                        if(paramTypes.length != rawArgs.length) return false;

                        for (int i = 0; i < paramTypes.length; i++) {
                            if(paramTypes[i].equals(Boolean.TYPE)) {
                                typedArgs.add(Boolean.valueOf(rawArgs[i]));
                            } else if (paramTypes[i].equals(Integer.TYPE)) {
                                typedArgs.add(Integer.valueOf(rawArgs[i]));
                            } else if (paramTypes[i].equals(Double.TYPE)) {
                                typedArgs.add(Double.valueOf(rawArgs[i]));
                            } else {
                                typedArgs.add(paramTypes[i].cast(rawArgs[i]));
                            }
                        }
                        isMatch = true;
                    } catch (ClassCastException ignored) {
                        typedArgs.clear();
                    }
                    return isMatch;
                })
                .findFirst();

        if (!firstSuitableConstructor.isPresent()) {
            throw new NoSuchMethodException();
        }

        return (BaseCommand) firstSuitableConstructor.get().newInstance(typedArgs.toArray());
    }

    /**
     * Handle command in CLI mode
     * @param commandId
     * @param args
     */
    private void invokeCommandByIdWithParams(String commandId, String[] args) {

        Class handlerClass = cachedCommandsById.get(commandId);
        BaseCommandHandler handler = cachedCommands.get(handlerClass);
        Constructor<?>[] commandConstructors = handler.getCommandClass().getDeclaredConstructors();

        BaseCommand commandInstance;
        try {
            commandInstance = instantiateAccordingToTypes(args, commandConstructors);
        } catch (Exception e) {
            System.err.println("Command constructor is incompatible with specified argument types");
            return;
        }

        this.beforeExecuteCommand(commandId, commandInstance);

        CommandResult result = new CommandResult();
        try {
            handler.handle(commandInstance, result);
            this.onCommandExecuted(commandId, commandInstance, result);
        } catch (Exception e) {
            this.onCommandException(commandId, commandInstance, e);
        } finally {
            afterHandleFinally();
        }
    }

    public final static class CommandResult<T> {

        private T result;

        private CommandResult() {
        }

        public void put(T content) {
            result = content;
        }

        /**
         * If a result is present, returns the result, otherwise throws
         * {@code NoSuchElementException}.
         * @return the non null value
         * @throws NoSuchElementException if no result is present
         */
        public T get() {
            if (result == null) {
                throw new NoSuchElementException("No result present");
            }
            return result;
        }


        public boolean isPresent() {
            return result != null;
        }
    }
}

package com.grape_soft.commands;

import java.lang.reflect.ParameterizedType;

public abstract class BaseCommandHandler<T extends BaseCommand, U> {

    protected Class commandClass;
    protected String commandId;

    public BaseCommandHandler() {
        super();
        this.commandClass = (Class) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.commandId = (getClass().getAnnotation(Command.class)).id();
    }


    public Class getCommandClass() {
        return this.commandClass;
    }

    public String getCommandId() {
        return this.commandId;
    }

    public void handle(T command, BaseCommandBus.CommandResult<U> output) throws Exception {
        handle(command);
    }

    public void handle(T command) throws Exception {

    }
}

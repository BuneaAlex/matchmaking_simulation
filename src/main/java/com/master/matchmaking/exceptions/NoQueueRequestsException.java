package com.master.matchmaking.exceptions;

public class NoQueueRequestsException extends Exception{

    public NoQueueRequestsException(final String message)
    {
        super(message);
    }

    public NoQueueRequestsException()
    {
        super("No queue requests in the database");
    }
}

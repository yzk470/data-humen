package com.dh.server.connector;

public interface Connector<TInput, TOutput> {
    TOutput execute(TInput input);
}

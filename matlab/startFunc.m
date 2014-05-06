function startFunc(block)
global server
    h = add_exec_event_listener(block, 'PostOutputs', @simExec);
    server.simStarted;    
end
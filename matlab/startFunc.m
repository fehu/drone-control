function startFunc(block)
global server
    h = add_exec_event_listener(block, 'PostOutputs', @simExec);
    server.simStarted;    
    server.simStarted;    
    server.simStarted;    
end
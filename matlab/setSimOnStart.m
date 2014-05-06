function setSimOnStart(model, bl)
    block = [model '/' bl];

    func = ['h = add_exec_event_listener(''' block ''', ''PostOutputs'', @simExec);'];
    set_param(model, 'StartFcn', func);
end

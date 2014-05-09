function s = startSim(name, execHookBlock)
    if gcs, close_system(gcs), end
    open_system(name);
    setSimOnStart(name, execHookBlock);
    s = sim(name);
end
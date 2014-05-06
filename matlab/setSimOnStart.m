function setSimOnStart(model, bl)
    block = [model '/' bl];
    set_param(model, 'StartFcn', ['startFunc(''' block ''')']);
end

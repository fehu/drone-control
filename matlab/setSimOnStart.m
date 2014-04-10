function setSimOnStart(block)
% blk = 'sl_quadrotor/Quadrotor plot/Plotter';
func = ['h = add_exec_event_listener(''' block ''', ''PostOutputs'', @simExec);'];
set_param('sl_quadrotor','StartFcn', func);
end

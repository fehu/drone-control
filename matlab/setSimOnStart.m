function setSimOnStart()
blk = 'sl_quadrotor/Quadrotor plot/Plotter';
func = ['h = add_exec_event_listener(''' blk ''', ''PostOutputs'', @simExec);'];
set_param('sl_quadrotor','StartFcn', func);
end

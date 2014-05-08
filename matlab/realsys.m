function [sys,x0,str,ts] = realsys(t,x,u,flag)

global uav_control uav_navdata 

 switch flag,
     case 0
        sizes = simsizes;
        sizes.NumContStates  = 0;
        sizes.NumDiscStates  = 0;
        sizes.NumOutputs     = 12;
        sizes.NumInputs      = 4;
        sizes.DirFeedthrough = 1;
        sizes.NumSampleTimes = 1;
        sys = simsizes(sizes); 
        x0=[];
        str=[];
        ts=[0 0];
     case 3
        uav_control.set = 1;
        uav_control.pitch = u(1);
        uav_control.roll = u(2);
        uav_control.yaw = u(3);
        uav_control.gaz = u(4);
        
        waitForNavdata();
        
        uav_navdata.read = 1;
        sys = [uav_navdata.x, uav_navdata.y, uav_navdata.z, ...
               uav_navdata.yaw, uav_navdata.pitch, uav_navdata.roll, ...
               uav_navdata.dx, uav_navdata.dy, uav_navdata.dz, ...
               uav_navdata.dyaw, uav_navdata.dpitch, uav_navdata.droll];
        
     case {1,2, 4, 9} % Unused flags
        sys = [];
     otherwise
        error(['unhandled flag = ',num2str(flag)]); 
 end

end

function waitForNavdata1()    
    t = timer('TimerFcn',@foo,...
        'ExecutionMode', 'fixedSpacing', ...
        'Period', 0.01, 'TasksToExecute', 1e12);
    
    start(t)
    Log('started timer', t)
    wait(t)
    Log('finished waiting timer')
end

function foo(a, b)
    Log('foo', a, b)
end

function waitForNavdata()    
global uav_navdata server
    Log('waiting for navdata')
    
    stop = 0;
    
    while ~stop
        res = char(server.execNext);                                           %%% server exec
        Log(['\t\t\t\t\tcheckForNavdata-server.execNext: ' res], '-debug')
        if uav_navdata.read == 0
            Log('new navdata', uav_navdata)
            stop = 1;
        elseif strcmp(res, 'stopped')
            Log('timer stopped')
            stop = 1;
        end 
    end
end


function waitForNavdataWithTimer()    
global timer_     
    timer_ = timer('TimerFcn',@checkForNavdata,...
        'ExecutionMode', 'fixedSpacing', ...
        'Period', 0.01, 'BusyMode', 'error',...
        'TasksToExecute', 1e12); % can't wait for infinite timer

    start(timer_)
    Log('started timer', timer_)
    wait(timer_)
    Log('finished waiting timer')
end

function checkForNavdata(x, y)    
global uav_navdata timer_ server
    Log('checkForNavdata', '-debug')
    res = 'aa'; %char(server.execNext);                                           %%% server exec
    Log(['\t\t\t\t\tcheckForNavdata-server.execNext: ' res], '-debug')
    
    if uav_navdata.read == 0
        Log('new navdata', uav_navdata)
        stop(timer_)
        
    elseif strcmp(res, 'stopped')
        Log('timer stopped')
        stop(timer_)
    end
end
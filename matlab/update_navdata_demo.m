function [sys,x0,str,ts] = update_navdata_demo(t,x,u,flag) % dx, dy, dz, droll, dpitch, dyaw
%get NAVDATA_DEMO from the model
global navdata_demo
    switch flag,
      case 0
        sizes = simsizes;
        sizes.NumContStates  = 0;
        sizes.NumDiscStates  = 0;
        sizes.NumOutputs     = 0;
        sizes.NumInputs      = 7;
        sizes.DirFeedthrough = 1;
        sizes.NumSampleTimes = 1;
        sys = simsizes(sizes); 
        x0  = [];
        str = [];          % Set str to an empty matrix.
        ts = [0.05 0];
      case 3
        % the velocity in the body frame [m]
        navdata.dx = u(1);
        navdata.dy = u(2);
        navdata.dz = u(3);

        % the roll/pitch/yaw rates in the body frame [rad/s]
        navdata.roll = u(4);
        navdata.pitch= u(5);
        navdata.yaw = u(6);

        navdata.altitude = u(7);
        
        navdata_demo = navdata;
        sys = [];
      case {1,2, 4, 9} % Unused flags
        sys = [];
      otherwise
        error(['unhandled flag = ',num2str(flag)]); % Error handling
end
end
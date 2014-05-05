function writeNavdata(x, y, z, pitch, roll, yaw, ...
                        dx, dy, dz, dpitch, droll, dyaw)
                    
global uav_navdata                    

    uav_navdata.x = x;
    uav_navdata.y = y;
    uav_navdata.z = z;
    uav_navdata.yaw = yaw;
    uav_navdata.pitch = pitch;
    uav_navdata.roll = roll;
    uav_navdata.dx = dx; 
    uav_navdata.dy = dy;
    uav_navdata.dz = dz;
    uav_navdata.dyaw = dyaw;
    uav_navdata.dpitch = dpitch;
    uav_navdata.droll = droll;

    uav_navdata.read  = 0;
end
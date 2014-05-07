function resp = readControl()
global uav_control

    Log('called read control', uav_control)

    if uav_control.set == 0
        resp.error = 'no new control data available';
    else
        resp = uav_control;
        uav_control.set = 0;
    end
end
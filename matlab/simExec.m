function simExec(block, eventData)
global server;
fprintf('executing Next');
%whos('server')
%feh.tec.matlab.server.Exec.next;
server.execNext;
%t = timer('TimerFcn',@(x,y)simExec(0),'StartDelay',1);
fprintf('executed Next');

function Log(varargin)
global flog

acc = '';
pref = '';

pref_debug = '[DEBUG]';
pref_info = '[INFO]';

if strcmp(varargin{end}, '-debug')
    pref = pref_debug;
    args = varargin(1:end-1);
else
    pref = pref_info;
    args = varargin;
end;

time = datestr(now,'mm/dd/yyyy HH:MM:SS.FFF'); % as in akka

for c = args
    acc = [acc '\n' pref ' [' time '] ' toString(c{:})];
end

fprintf(flog, acc);

end

function str = toString(x)
    if ismatrix(x) && isnumeric(x)
        str = mat2str(x);
    elseif isstruct(x) || isobject(x)
        str = fieldsToString(x);
    elseif ischar(x)
        str = x;
    elseif isjava(x)
        str = char(x);
    end 
end

function str = fieldsToString(x)
    [k v] = struct2nv(x);

    str = '';
    for i=1:length(k)
        str = [str ' ' k{i} ': ' toString(v{i}) ';'];
    end    
end
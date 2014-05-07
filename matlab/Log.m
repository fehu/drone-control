function Log(varargin)
global flog

acc = '';

for c = varargin
    acc = [acc '\n' toString(c{1})];
end

fprintf(flog, acc);

end

function str = toString(x)
    if ismatrix(x) && isnumeric(x)
        str = mat2str(x);
    elseif isstruct(x)
        [k v] = struct2nv(x);

        str = '';
        for i=1:length(k)
            str = [str ' ' k{i} ': ' toString(v{i}) ';']
        end    
    elseif ischar(x)
        str = x;
    elseif isjava(x)
        str = x.toString;   
    end 
end
function parExec(threads)
global trea;
    f = fopen('tmp.txt', 'w');
    fprintf(f, '!!!\n');
    fprintf(f, '%i\n', length(threads));
    who = whos('threads');
    fprintf(f, 'name: %s size: %i bytes: %i class: %s\n', who.name, who.size, who.bytes, who.class);
    fprintf(f, 'end!!!');   
    fclose(f);
%    details
    trea = threads;
    matObj = matfile('tmp.mat','Writable',true) ;
    matObj.savedVar(0) =  trea;
    
    parfor i = 1:length(threads);
       thread = threads{i};
%        f = fopen('tmp.txt', 'w');
%        fprintf(f, '%s', thread.toString);
%        fclose(f);

       thread.run();
    end
end

#find ./src -name "*.java" > sources_list.txt
#This works on linux and cygwin, using *.java kept failing under find.
ls -R ./src | awk '
/:$/&&f{s=$0;f=0}
/:$/&&!f{sub(/:$/,"");s=$0;f=1;next}
NF&&f{ print s"/"$0 }'| grep '.java' > sources_list.txt
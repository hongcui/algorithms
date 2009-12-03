use strict;
use DBI; #load module
use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
use SentenceSpliter;
use SeedDescriptionExtraction;
use unsupervisedOrganNameExtraction;

my $db = "morph_descrpt_ant";
my $host = "localhost";
my $user = "termsuser";
my $password = "termspassword";
my $dbh = DBI->connect("DBI:mysql:host=$host", $user, $password)
or die DBI->errstr."\n";

my $test = $dbh->prepare('create database if not exists '.$db.' CHARACTER SET utf8') or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

$test = $dbh->prepare('use '.$db) or die $dbh->errstr."\n";
$test->execute() or die $test->errstr."\n";

my $select = $dbh->prepare('select paragraph from paragraphs');
$select->execute();
my $count = 0;
while(my ($p) = $select->fetchrow_array()){
	print $count++."\n";
	if (isList($p) == 1){
		print $p."\n" ;
		print "";
	}
}


sub isList{
	my $p = shift;
	my $list_a = "[,;\\.:] a([\\.,:)\\]]).*?\\w+?.*?[.;\\.] b\\1.*?\\w+?.*?";
	my $list_1 = "[,;\\.:] 1([\\.,:\\]]).*?\\w+?.*?[.;\\.] 2\\1.*?\\w+?.*?";
	my $list_i = "[,;\\.:] i([\\.,:\\]]).*?\\w+?.*?[.;\\.] ii\\1.*?\\w+?.*?";
	my $list_abbr = "[,;\\.:] [A-Z]{2,}([:,])([^A-Z]{4,})[,;\\.] [A-Z]{2,}\\1([^A-Z]{4,})";
	if ($p =~ /$list_a/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_1/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_i/i and $p=~/^fig(ure|ures|\.)/i){
		return 1;
	}elsif($p =~ /$list_abbr/ and $2!~/\d/ and $3!~/\d/){
		return 1;	 
	}else{
		return 0;
	}
}
use strict;
use DBI; #load module
use lib '..\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
#use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\Description_Extraction\\paragraphExtraction\\';
use SentenceSpliter;
use SeedDescriptionExtraction;
use unsupervisedOrganNameExtraction;

print isAcronymDef("HL: Head length ab");
sub isAcronymDef{
	my $p = shift;
	my $list_abbr = "(.*?)([A-Z]{2,})(.*)";
	if($p =~ /$list_abbr/){	
		my $pc = $p;
		my $conf = 0;
		while($pc=~/$list_abbr/){
			$pc = $3;
			my $target = $2;
			my $count = length($target);
			my $words_before = takeWords($count, $1, -1); #before
			my $words_after = takeWords($count, $3, 1); #after
			my $wb = $words_before;
			my $wa = $words_after;
			$words_before = "@#$%^&*" if $words_before !~ /\w/;
			$words_after = "@#$%^&*" if $words_after !~ /\w/;			
			$words_before =~ s#[$target]##ig;
			$words_after=~ s#[$target]##ig;
			if(abs(length($wb) - $count) <=1 and $pc !~/\d\s*\.\s*\d/ and $words_before eq ""){
				$conf++;
			}elsif(abs(length($wa) - $count) <=1 and $pc !~/\d\s*\.\s*\d/ and $words_after eq ""){
				$conf++;
			}			
		}
		return 1 if $conf >= 1;	 
	}
	return 0;
}
sub takeWords{
	my ($count, $text, $dir) = @_;
	$text =~ s#^\W+# # if $dir > 0;
	$text =~ s#\W+$# # if $dir < 0;
	$text =~ s#[;:,\.)(\]\[{}].*## if $dir > 0; #remove everything from the first punct on
	$text =~ s#.*?([^;:,\.)(\]\[{}]*$)#$1# if $dir < 0;
	$text =~ s#\s+# #g;
	$text =~ s#^\s+##g;
	$text =~ s#\s+$##g;
	my $result = "";
	my @words = split(/\s+/, $text);
	if($dir > 0){#after
		for(my $i = 0; $i<$count; $i++){
			#$result.=$words[$i];
			$result .= substr($words[$i], 0, 1);
		}
	}else{#before
		my $l = @words > $count? @words-$count : 0;
		for(my $i = @words-1; $i>=$l; $i--){
			#$result.=$words[$i];
			$result = substr($words[$i], 0, 1).$result;
		}
	}
	#$result =~s#\W##g;
	return $result;
	
}
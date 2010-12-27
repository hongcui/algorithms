#!/usr/bin/perl
package SeedDescriptionExtraction;

use strict;
use DBI;
use lib 'C:\\Docume~1\\hongcui\\Desktop\\WordNov2009\\\Description_Extraction\\paragraphExtraction\\UnsupervisedClauseMarkup\\';
use SentenceSpliter;



sub collectSeedParagraphs{
	my ($db, $paragraphtable, $prefix) = @_; 
	my @seeds = ();
	my $debug = 0;

	#my @stop_words = ( #removed (the,a,an) and (is,are)
#"about", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", 
#"always","among", "amongst", "amoungst", "and", "another", "any", "anyhow", "anyone", "anything", "anyway", "anywhere", "around", "as", "at", "be", "been", "before", "behind", "being", "below", "beside", "besides", "between", "beyond", "both", "but", "by", "can", "cannot", "cant", "co", "could", "couldn't", "de", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "few", "fifteen", "fify", "first", "five", "for", "forty", "four", "from", "front", "get", "go", "had", "has", "hasn't", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "i", "i.e.", "if", "in", "inc", "indeed", "into", "it", "its", "itself", "ltd", "may", "me", "might", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "on", "one", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "put", "re", "seemed", "seeming", "seems", "she", "should", "since", "six", "sixty", "so", "some", "somehow", "someone","something", "sometime", "sometimes", "somewhere", "still", "such", "ten", "than", "that","their", "them", "themselves","then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "third", "this", "those", "though", "three", "through","throughout", "thru", "thus", "to", "together", "too", "top", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves");

	my $fig_table = "Fig\.|Figs\.|Figure|Figures|Table|Tables";
	my $punctuations ="[!\"#$%&'()*+,-\./:;<=>?@\[\]^_`\\{|}~]";
	my $articles = "a|an|the";	
	my $be = "am|is|are|was|were|be|being|been";
	my $pronouns = "I|he|him|me|she|her|my|mine|his|our|we|ours|you|your|yours|author|authors";
	my $smallunits = "cm|mm";
	my $bigunits = "m|mi|ft|yd|meter|meters|mile|miles|foot|feet|yard|yards";
		
	my $length_threshold = 10; ## of sentences
	#my $length_threshold = 200; ## of words
		
	my $host = "localhost";
	my $user = "termsuser";
	my $password = "termspassword";
	my $dbh = DBI->connect("DBI:mysql:database=$db;host=$host", $user, $password)
	or die DBI->errstr."\n";

	my $select = $dbh->prepare('select paraID, paragraph from '.$prefix.'_paragraphs');
	$select->execute() or warn "$select->errstr\n";
	while(my($pid, $p) = $select->fetchrow_array()){
		my $length = 0;
		#my $words_not_found = 0;
		my $capital = 0;
		#my $stop_words = 0;
		my $distance = 0;
		my $pronoun = 0;
		my $figures = 0;
		my $punc = 0;
		#my $noun = 0;
		#my $verb = 0;
		#my $adj = 0;
		#my $adv = 0;
		my $articles = 0;
		my $isare = 0;
		my $url_email = 0;
		my $forexample = 0;
		my $andothers = 0;
		#my $unknown = 0;
		
	
	
		my @sentences_length;
		my @sentences;
		my $sum = 0;
		my $ad = 0;
		my $median = 0;

		
		#make a copy of the current line for later print
		my $p_copy = $p;
		$p =~ s#(www\.|http:\/\/|\w+@|urn:)\S+#urlemailurn#gi;
		$p =~ s#e\.g\.#exampleexampleexample#gi;
		$p =~ s#et\s+al\.#andothersandothers#gi;
		$p =~ s#[^a-zA-Z0-9 !"\#$%&'()*+,-\./:;<=>?@\[\]^_`\\{|}~]# #g; #non-printables => 1 space
		$p =~ s#\s+($punctuations+)#\1 #g; #attach a punct with the preceeding token "in the woods ,lake" => "in the woods, lake"
		$p =~ s#\s+# #g; #spaces => 1 space
		$p =~ s/^\s*//;
		$p =~ s/\s*\n$//; #trim				

		my @words = split(/\s+/,$p);
		@words = grep (/\w/, @words);
		#my $length = @words;
		
		@sentences = SentenceSpliter::get_sentences($p); #line -> sentences
		my $length = @sentences;
		if ( $length < $length_threshold ) { #do not consider paragraphs shorter than $length_threshold sentences 
			next;
		}
		($ad, $median) = absoluteDeviationInLength (@sentences);

		#make feature list	
		foreach (@words){
			if (/^[A-Z\.]+$/) {
				$capital++;
			}
		}
			
		$figures = $p =~ s#\b($fig_table) (\d+)?##gi; #remove matched fig. 5 or "fig. "e
		$distance = $p =~ s#\b\d+(\.\d+)? ($bigunits)\b##gi;
		$url_email = $p =~ s#urlemailurn##gi;
		$forexample = $p =~ s#examplesexamplesexamples##g;
		$andothers = $p =~ s#andothersandothers##g;
		$articles = $p=~ s#\b($articles)\b##gi;
		$isare = $p=~ s#\b($be)\b##gi;
		$pronoun = $p=~s#\b($pronouns)\b##gi;
		$punc = $p=~ s#["']##g; 
		my $year = $p=~ s#\b1[789]\d\d\b##g;
		$year += $p=~ s#\b200\d\b##g;
			
		if ($median >= 7 &&
			$ad >= 5 &&
			$capital/$length < 0.6 &&
			$figures/$length < 0.1 &&
			$distance <= 0 &&
			$url_email <=0 &&
			$andothers <=0 &&
			$forexample <=0 &&
			$articles/$length < 0.1 &&
			$isare/$length < 0.1 &&
			$pronoun <= 0 &&
			$punc<= 0 &&
			$year/$length < 0.1){
				print $p_copy if $debug;
				push(@seeds, $pid);			
		}

    }
    return @seeds;
}
 


sub median { 
	my @sentences = @_;
	my @pole = ();
	foreach (@sentences) {
		my @sentences_words = split( /\s+/, $_ );
		my $len = @sentences_words."\n";
		push(@pole,$len);
	}
    my $pole = scalar @pole;

    @pole = sort { $a <=> $b } @pole;

    if( ($pole % 2) == 1 ) {
        return $pole[(($pole+1)/2)-1];
    } 
	else {
        return ($pole[($pole/2)-1] + $pole[$pole/2])/2;
    }
}


sub absoluteDeviationInLength {
	my @sentences        = @_;
	my @sentences_length = ();
	my ($ad, $sum, $median);
	#calcuate absolute deviation of sentence length in a p

	my $median = median(@sentences);
	
	foreach (@sentences) {
		my @sentences_words = split( /\s+/, $_ );
		my $len = @sentences_words."\n";
		push(@sentences_length,$len);
	}

	foreach (@sentences_length) {
		$sum += abs(scalar($_) - $median );
	}
	if (scalar(@sentences) != 0 ) {
		$ad = $sum / scalar(@sentences);
	}
	return ($ad,$median);
}

1;
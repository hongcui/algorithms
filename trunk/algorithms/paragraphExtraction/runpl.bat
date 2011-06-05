REM commands to run bootstrapDescriptionExtraction.pl on three datasets: bhl, plaziants, and treatisetest

perl bootstrapDescriptionExtraction.pl d bhl_paragraphs paragraphbootstrappingevaluation plain Z:\seeds bhl_03_10_05_20_05 0.3 10 0.5 20 0.5 bhl_benchmark bhl_paragraphs

perl -d bootstrapDescriptionExtraction.pl d plaziants_paragraphs paragraphbootstrappingevaluation plain Z:\seeds plaziants_03_10_05_20_05 0.3 10 0.5 20 0.5 plaziants_benchmark plaziants_paragraphs

perl bootstrapDescriptionExtraction.pl d treatisetest_paragraphs paragraphbootstrappingevaluation plain Z:\seeds treatisetest_03_10_05_20_05 0.3 10 0.5 20 0.5 treatisetest_benchmark treatisetest_paragraphs